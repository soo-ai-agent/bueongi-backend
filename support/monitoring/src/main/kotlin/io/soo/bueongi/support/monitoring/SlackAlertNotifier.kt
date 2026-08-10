package io.soo.bueongi.support.monitoring

import com.fasterxml.jackson.databind.ObjectMapper
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

/** webhook URL(시크릿)은 로그·예외 메시지에 싣지 않는다. */
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

    override fun notify(key: String, title: String, detail: String) {
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
        return snapshot.asReversed().take(limit)
    }

    override fun sendTest(): Boolean {
        if (!config.sendEnabled()) {
            return false
        }
        return try {
            val response: ResponseEntity<Void> = restClient.post()
                .uri(URI.create(config.webhookUrl()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(mapOf("text" to TEST_MESSAGE)))
                .retrieve()
                .toBodilessEntity()
            response.statusCode.is2xxSuccessful
        } catch (e: Exception) {
            log.warn("Slack 테스트 발송 실패(무시): {}", e.message)
            false
        }
    }

    private fun recordAlert(key: String, title: String, detail: String) {
        val record: AlertRecord = AlertRecord(atEpochMs = nowMs(), key = key, title = title, detail = detail)
        synchronized(recentAlerts) {
            recentAlerts.addLast(record)
            while (recentAlerts.size > ALERT_HISTORY_CAP) {
                recentAlerts.removeFirst()
            }
        }
    }

    private fun send(title: String, detail: String) {
        try {
            restClient.post()
                .uri(URI.create(config.webhookUrl()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(mapOf("text" to ":rotating_light: *$title*\n$detail")))
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.warn("Slack 알림 발송 실패(무시): {}", e.message)
        }
    }

    private companion object {
        const val ALERT_HISTORY_CAP: Int = 200

        const val TEST_MESSAGE: String = ":white_check_mark: bueongi 운영 알림 테스트 — 이 메시지가 보이면 Slack 연동이 정상입니다."

        fun newDaemonSingleThreadExecutor(): Executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "slack-alert").apply { isDaemon = true }
        }
    }
}
