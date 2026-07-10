package io.soo.bueongi.support.monitoring

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Slack Incoming Webhook 알림기 — `SLACK_WEBHOOK_URL`(monitoring.yml: `bueongi.alert.slack-webhook-url`)이
 * 설정되면 [isEnabled]=true. 미설정이면 모든 알림이 무해하게 무시된다(기존 동작 불변).
 *
 * - 스로틀: 같은 key 는 `bueongi.alert.throttle-minutes`(기본 10분) 안에서 1회만 — 장애 중 알림 폭주 방지.
 *   판정은 [ConcurrentHashMap.compute] 로 원자적이라 동시 요청에도 중복 발송되지 않는다.
 * - 비동기: 데몬 단일 스레드에서 발송 — 사용자 요청 처리를 절대 막지 않고, 발송 실패는 로그로만 남긴다.
 * - 시크릿 비노출: webhook URL 은 로그·예외 메시지에 싣지 않는다.
 *
 * 생성자 2개: Spring 은 @Autowired 4-인자 생성자로 만들고(발송 스레드·벽시계 기본값),
 * 테스트는 주 생성자로 동기 실행기·가짜 시계를 주입해 결정론적으로 검증한다.
 */
@Component
class SlackAlertNotifier internal constructor(
    webhookUrl: String,
    throttleMinutes: Long,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
    /** 발송 실행기 — 운영은 데몬 단일 스레드, 테스트는 동기 실행기. */
    private val executor: Executor,
    /** 현재 시각(ms) 공급자 — 스로틀 판정용(테스트에서 시간 진행 주입). */
    private val nowMs: () -> Long,
) : AlertNotifier {

    @Autowired
    constructor(
        @Value("\${bueongi.alert.slack-webhook-url:}") webhookUrl: String,
        @Value("\${bueongi.alert.throttle-minutes:10}") throttleMinutes: Long,
        restClientBuilder: RestClient.Builder,
        objectMapper: ObjectMapper,
    ) : this(
        webhookUrl = webhookUrl,
        throttleMinutes = throttleMinutes,
        restClientBuilder = restClientBuilder,
        objectMapper = objectMapper,
        executor = newDaemonSingleThreadExecutor(),
        nowMs = System::currentTimeMillis,
    )

    private val log = LoggerFactory.getLogger(SlackAlertNotifier::class.java)
    private val webhookUrl = webhookUrl.trim()
    private val throttleMs = throttleMinutes * 60_000
    private val restClient = restClientBuilder.clone().build()
    private val lastSentAtMs = ConcurrentHashMap<String, Long>()

    override val isEnabled: Boolean
        get() = webhookUrl.isNotEmpty()

    override fun notify(key: String, title: String, detail: String) {
        if (!isEnabled) {
            log.debug("Slack 알림 비활성(webhook 미설정) — 무시: {}", key)
            return
        }
        val now = nowMs()
        var allowed = false
        lastSentAtMs.compute(key) { _, last ->
            if (last == null || now - last >= throttleMs) {
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

    private fun send(title: String, detail: String) {
        try {
            restClient.post()
                .uri(URI.create(webhookUrl))
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
        fun newDaemonSingleThreadExecutor(): Executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "slack-alert").apply { isDaemon = true }
        }
    }
}
