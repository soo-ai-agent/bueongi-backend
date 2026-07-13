package io.soo.bueongi.support.monitoring

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Slack Incoming Webhook 알림기 — 발송 설정([SlackAlertConfig])이 효과 발송 가능([SlackAlertConfig.sendEnabled])이면
 * 알림을 보낸다. webhook 미설정/토글 off 면 모든 알림이 무해하게 무시된다(기존 동작 불변).
 *
 * - 스로틀: 같은 key 는 [SlackAlertConfig.throttleMillis] 창 안에서 1회만 — 장애 중 알림 폭주 방지.
 *   판정은 [ConcurrentHashMap.compute] 로 원자적이라 동시 요청에도 중복 발송되지 않는다.
 * - 뮤트: [SlackAlertConfig.isMuted] 카테고리는 **발송만** 생략한다(이력은 그대로 남긴다).
 * - 비동기: [notify] 는 데몬 단일 스레드에서 발송 — 사용자 요청 처리를 절대 막지 않고, 실패는 로그로만 남긴다.
 * - 시크릿 비노출: webhook URL 은 로그·예외 메시지에 싣지 않는다([SlackAlertConfig.webhookUrl] 로만 접근).
 *
 * 생성자 2개: Spring 은 @Autowired 3-인자 생성자로 만들고(발송 스레드·벽시계 기본값),
 * 테스트는 주 생성자로 동기 실행기·가짜 시계를 주입해 결정론적으로 검증한다.
 */
@Component
class SlackAlertNotifier internal constructor(
    private val config: SlackAlertConfig,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
    /** 발송 실행기 — 운영은 데몬 단일 스레드, 테스트는 동기 실행기. */
    private val executor: Executor,
    /** 현재 시각(ms) 공급자 — 스로틀 판정용(테스트에서 시간 진행 주입). */
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

    private val log = LoggerFactory.getLogger(SlackAlertNotifier::class.java)
    private val restClient = restClientBuilder.clone().build()
    private val lastSentAtMs = ConcurrentHashMap<String, Long>()

    /** 장애 이력 링버퍼 — 자기 자신을 락으로 삼아 동기화한다. 최신 [ALERT_HISTORY_CAP]건만 보관(오래된 것부터 폐기). */
    private val recentAlerts = ArrayDeque<AlertRecord>()

    override val isEnabled: Boolean
        get() = config.sendEnabled()

    override fun notify(key: String, title: String, detail: String) {
        // 이력은 발송 스로틀·활성·뮤트와 독립하게 매 호출 기록한다 — 전송이 생략돼도 "장애 발생"은 남긴다.
        recordAlert(key, title, detail)
        if (!config.sendEnabled()) {
            log.debug("Slack 알림 비활성(webhook 미설정 또는 토글 off) — 무시: {}", key)
            return
        }
        if (config.isMuted(key)) {
            log.debug("Slack 알림 뮤트 카테고리 — 발송 생략(이력은 남김): {}", key)
            return
        }
        val now = nowMs()
        var allowed = false
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
        val snapshot = synchronized(recentAlerts) { recentAlerts.toList() }
        // 버퍼는 오래된→최신 순으로 쌓이므로 뒤집어 최신순으로 돌려준다.
        return snapshot.asReversed().take(limit)
    }

    override fun sendTest(): Boolean {
        if (!config.sendEnabled()) {
            return false
        }
        return try {
            val response = restClient.post()
                .uri(URI.create(config.webhookUrl()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(mapOf("text" to TEST_MESSAGE)))
                .retrieve()
                .toBodilessEntity()
            response.statusCode.is2xxSuccessful
        } catch (e: Exception) {
            // 테스트 발송 실패도 서비스에 전이시키지 않는다. URL(시크릿) 비노출 — 예외 메시지만.
            log.warn("Slack 테스트 발송 실패(무시): {}", e.message)
            false
        }
    }

    private fun recordAlert(key: String, title: String, detail: String) {
        val record = AlertRecord(atEpochMs = nowMs(), key = key, title = title, detail = detail)
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
                // 사전 직렬화(String) — Content-Length 전송(chunked 미사용)으로 수신측 호환성을 높인다.
                .body(objectMapper.writeValueAsString(mapOf("text" to ":rotating_light: *$title*\n$detail")))
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            // 알림 실패는 서비스에 전이시키지 않는다. URL(시크릿) 비노출 — 예외 메시지만.
            log.warn("Slack 알림 발송 실패(무시): {}", e.message)
        }
    }

    private companion object {
        /** 장애 이력 링버퍼 용량 — 최신 이 개수만 보관한다. */
        const val ALERT_HISTORY_CAP = 200

        /** 관리자 테스트 발송 메시지 — 실제 장애가 아님을 수신측이 알 수 있게 고정 문구. */
        const val TEST_MESSAGE = ":white_check_mark: bueongi 운영 알림 테스트 — 이 메시지가 보이면 Slack 연동이 정상입니다."

        fun newDaemonSingleThreadExecutor(): Executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "slack-alert").apply { isDaemon = true }
        }
    }
}
