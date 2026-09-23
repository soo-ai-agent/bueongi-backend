package io.soo.bueongi.client.slack

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.support.monitoring.ALERT_HISTORY_MAX_LIMIT
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.AlertRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

// webhook URL 은 경로에 토큰이 든 시크릿이라 로그·예외 메시지에 싣지 않는다.
@Component
class SlackAlertNotifier internal constructor(
    private val config: SlackAlertConfig,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
    private val executor: Executor,
    private val nowMs: () -> Long,
) : AlertNotifier {
    @Autowired
    constructor(
        config: SlackAlertConfig,
        restClientBuilder: RestClient.Builder,
        objectMapper: ObjectMapper,
    ) : this(
        config = config,
        restClientBuilder = restClientBuilder,
        objectMapper = objectMapper,
        executor = newDaemonSingleThreadExecutor(),
        nowMs = System::currentTimeMillis,
    )

    private val log: Logger = LoggerFactory.getLogger(SlackAlertNotifier::class.java)
    private val restClient: RestClient = restClientBuilder.clone().build()
    private val lastSentAtMs: ConcurrentHashMap<String, Long> = ConcurrentHashMap<String, Long>()

    private val recentAlerts: ArrayDeque<AlertRecord> = ArrayDeque<AlertRecord>()

    override val isEnabled: Boolean
        get() = config.sendEnabled()

    override fun notify(
        key: String,
        title: String,
        detail: String,
    ) {
        recordAlert(key, title, detail)
        if (!config.sendEnabled()) {
            log.debug("Slack 알림 비활성(webhook 미설정 또는 토글 off) — 무시: {}", key)
            return
        }
        if (config.isMuted(key)) {
            log.debug("Slack 알림 뮤트 카테고리 — 발송 생략(이력은 남김): {}", key)
            return
        }
        val now: Long = nowMs()
        var allowed: Boolean = false
        lastSentAtMs.compute(key) { _, last ->
            if (last == null || now - last >= config.throttleMillis()) {
                allowed = true
                now
            } else {
                last
            }
        }
        if (!allowed) {
            log.debug("Slack 알림 스로틀 — 억제: {}", key)
            return
        }
        executor.execute { send(title, detail) }
    }

    override fun findRecentAlerts(limit: Int): List<AlertRecord> {
        val snapshot: List<AlertRecord> = synchronized(recentAlerts) { recentAlerts.toList() }
        return snapshot.asReversed().take(limit.coerceIn(1, ALERT_HISTORY_MAX_LIMIT))
    }

    override fun sendTest(): Boolean {
        if (!config.sendEnabled()) {
            return false
        }
        return try {
            val response: ResponseEntity<Void> =
                restClient.post()
                    .uri(URI.create(config.webhookUrl()))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(objectMapper.writeValueAsString(mapOf("text" to TEST_MESSAGE)))
                    .retrieve()
                    .toBodilessEntity()
            response.statusCode.is2xxSuccessful
        } catch (e: Exception) {
            log.warn(failureLine(e, subject = "발송 시험", action = "시험 실패로 응답(운영 알림은 그대로)"))
            false
        }
    }

    private fun recordAlert(
        key: String,
        title: String,
        detail: String,
    ) {
        val record = AlertRecord(atEpochMs = nowMs(), key = key, title = title, detail = detail)
        synchronized(recentAlerts) {
            recentAlerts.addLast(record)
            while (recentAlerts.size > ALERT_HISTORY_CAP) {
                recentAlerts.removeFirst()
            }
        }
    }

    private fun send(
        title: String,
        detail: String,
    ) {
        try {
            restClient.post()
                .uri(URI.create(config.webhookUrl()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(mapOf("text" to ":rotating_light: *$title*\n$detail")))
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.warn(failureLine(e, subject = "운영 알림", action = "알림만 유실(이력은 남음)"))
        }
    }

    private fun failureLine(
        e: Throwable,
        subject: String,
        action: String,
    ): String {
        return "$PROVIDER 호출 실패 | $subject | 원인=${e.message.orEmpty()} | 조치=$action"
    }

    private companion object {
        const val ALERT_HISTORY_CAP: Int = 200

        // 송신 줄의 원천 이름과 같아야 세로로 짝이 보인다.
        const val PROVIDER: String = "slack"

        const val TEST_MESSAGE: String = ":white_check_mark: bueongi 운영 알림 테스트 — 이 메시지가 보이면 Slack 연동이 정상입니다."

        fun newDaemonSingleThreadExecutor(): Executor =
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "slack-alert").apply { isDaemon = true }
            }
    }
}
