package io.soo.bueongi.support.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.Executor

/**
 * [SlackAlertNotifier] 계약 테스트 — 발송 페이로드, 비활성 무해, key 스로틀(억제/창 경과 재발송),
 * 발송 실패 삼킴을 고정한다. 발송은 동기 실행기로 바꿔 결정론적으로 검증한다.
 */
class SlackAlertNotifierTest {

    private val webhook = "https://hooks.slack.com/services/T000/B000/XXXX"

    /** 스로틀 검증용 가변 시계 — 테스트가 now 를 직접 진행시킨다. */
    private var now = 1_000_000L

    private fun bind(url: String = webhook, throttleMinutes: Long = 10): Pair<SlackAlertNotifier, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val notifier = SlackAlertNotifier(
            webhookUrl = url,
            throttleMinutes = throttleMinutes,
            restClientBuilder = builder,
            objectMapper = ObjectMapper(),
            executor = Executor { it.run() }, // 테스트: 동기 발송
            nowMs = { now },
        )
        return notifier to server
    }

    @Test
    fun `정상 - webhook 으로 제목·상세를 담은 text 페이로드를 POST 한다`() {
        val (notifier, server) = bind()
        server.expect(requestTo(webhook))
            .andExpect(content().string(containsString("Tmap 경로 API 실패")))
            .andExpect(content().string(containsString("폴백 경로로 응답 중")))
            .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

        notifier.notify("tmap-route-fallback", "Tmap 경로 API 실패", "폴백 경로로 응답 중")

        server.verify()
    }

    @Test
    fun `비활성 - URL 미설정이면 isEnabled=false 이고 어떤 호출도 나가지 않는다`() {
        val (notifier, server) = bind(url = " ")
        server.expect(ExpectedCount.never(), requestTo(containsString("hooks.slack.com")))
            .andRespond(withSuccess())

        assertThat(notifier.isEnabled).isFalse()
        assertThatCode { notifier.notify("k", "t", "d") }.doesNotThrowAnyException()
        server.verify()
    }

    @Test
    fun `스로틀 - 같은 key 는 창 안에서 1회만, 다른 key 는 발송, 창 경과 후 재발송`() {
        val (notifier, server) = bind(throttleMinutes = 10)
        // 발송 3회만 기대: key-a(최초) + key-b + key-a(창 경과 후).
        server.expect(ExpectedCount.times(3), requestTo(webhook)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

        notifier.notify("key-a", "t", "d") // 발송 1
        notifier.notify("key-a", "t", "d") // 억제(같은 key, 창 내)
        notifier.notify("key-b", "t", "d") // 발송 2(다른 key)
        now += 10 * 60_000L // 스로틀 창 경과
        notifier.notify("key-a", "t", "d") // 발송 3

        server.verify()
    }

    @Test
    fun `실패 삼킴 - webhook 이 5xx 여도 예외가 호출자에게 전이되지 않는다`() {
        val (notifier, server) = bind()
        server.expect(requestTo(webhook)).andRespond(withServerError())

        assertThatCode { notifier.notify("k", "t", "d") }.doesNotThrowAnyException()
    }
}
