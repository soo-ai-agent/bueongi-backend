package io.soo.bueongi.client.slack

import io.soo.bueongi.support.monitoring.ALERT_HISTORY_MAX_LIMIT

import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.AlertRecord

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

class SlackAlertNotifierTest {

    private val webhook: String = "https://hooks.slack.com/services/T000/B000/XXXX"

    private var now: Long = 1_000_000L

    private fun bind(
        url: String = webhook,
        throttleMinutes: Int = 10,
        mutedCategories: Set<String> = emptySet(),
    ): Pair<SlackAlertNotifier, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val config = SlackAlertConfig(webhookUrl = url, throttleMinutes = throttleMinutes)
        if (mutedCategories.isNotEmpty()) {
            config.update(enabled = true, throttleMinutes = throttleMinutes, mutedCategories = mutedCategories)
        }
        val notifier = SlackAlertNotifier(
            config = config,
            restClientBuilder = builder,
            objectMapper = ObjectMapper(),
            executor = Executor { it.run() },
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
        server.expect(ExpectedCount.times(3), requestTo(webhook)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

        notifier.notify("key-a", "t", "d")
        notifier.notify("key-a", "t", "d")
        notifier.notify("key-b", "t", "d")
        now += 10 * 60_000L
        notifier.notify("key-a", "t", "d")

        server.verify()
    }

    @Test
    fun `실패 삼킴 - webhook 이 5xx 여도 예외가 호출자에게 전이되지 않는다`() {
        val (notifier, server) = bind()
        server.expect(requestTo(webhook)).andRespond(withServerError())

        assertThatCode { notifier.notify("k", "t", "d") }.doesNotThrowAnyException()
    }

    @Test
    fun `이력 - notify 마다 기록하고 최신순으로 반환한다(비활성이어도 기록)`() {
        val (notifier, _) = bind(url = " ")
        now = 100L
        notifier.notify("k1", "t1", "d1")
        now = 200L
        notifier.notify("k2", "t2", "d2")

        val recent: List<AlertRecord> = notifier.findRecentAlerts(10)
        assertThat(recent.map { it.key }).containsExactly("k2", "k1")
        assertThat(recent[0].atEpochMs).isEqualTo(200L)
    }

    @Test
    fun `이력 - 스로틀로 발송이 억제돼도 이력은 매 호출 남는다(스로틀 독립)`() {
        val (notifier, server) = bind(throttleMinutes = 10)
        server.expect(ExpectedCount.times(1), requestTo(webhook)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

        notifier.notify("k", "t", "d")
        notifier.notify("k", "t", "d")

        server.verify()
        assertThat(notifier.findRecentAlerts(10)).hasSize(2)
    }

    @Test
    fun `이력 - 용량(200)을 넘으면 오래된 것부터 폐기한다(capped)`() {
        val (notifier, _) = bind(url = " ")
        repeat(250) { notifier.notify("k$it", "t", "d") }

        val recent: List<AlertRecord> = notifier.findRecentAlerts(1000)
        assertThat(recent).hasSize(200)
        assertThat(recent.first().key).isEqualTo("k249")
        assertThat(recent.last().key).isEqualTo("k50")
    }

    @Test
    fun `뮤트 - key 의 카테고리(콜론 앞부분)가 뮤트면 발송 생략, 이력은 남긴다`() {
        val (notifier, server) = bind(mutedCategories = setOf("api-500"))
        server.expect(ExpectedCount.never(), requestTo(webhook)).andRespond(withSuccess())

        notifier.notify("api-500:NullPointerException", "t", "d")

        server.verify()
        assertThat(notifier.findRecentAlerts(10)).hasSize(1)
    }

    @Test
    fun `뮤트 - 뮤트되지 않은 카테고리는 정상 발송된다`() {
        val (notifier, server) = bind(mutedCategories = setOf("api-500"))
        server.expect(ExpectedCount.times(1), requestTo(webhook)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

        notifier.notify("tmap-route-fallback", "t", "d")

        server.verify()
    }

    @Test
    fun `테스트 발송 - sendEnabled 이면 동기 POST 하고 2xx 면 true`() {
        val (notifier, server) = bind()
        server.expect(requestTo(webhook)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

        assertThat(notifier.sendTest()).isTrue()

        server.verify()
    }

    @Test
    fun `테스트 발송 - 5xx 면 예외 삼키고 false`() {
        val (notifier, server) = bind()
        server.expect(requestTo(webhook)).andRespond(withServerError())

        assertThat(notifier.sendTest()).isFalse()

        server.verify()
    }

    @Test
    fun `테스트 발송 - 비활성(webhook 미설정)이면 발송 없이 false`() {
        val (notifier, server) = bind(url = " ")
        server.expect(ExpectedCount.never(), requestTo(containsString("hooks.slack.com"))).andRespond(withSuccess())

        assertThat(notifier.sendTest()).isFalse()

        server.verify()
    }

    @Test
    fun `이력 조회 limit 은 상한으로 절삭되고 0 이하는 최소 1 이 된다`() {
        val (notifier, server) = bind()
        server.expect(ExpectedCount.manyTimes(), requestTo(webhook)).andRespond(withSuccess())
        repeat(3) { index ->
            now += 60L * 60L * 1000L
            notifier.notify(key = "k$index", title = "t$index", detail = "d")
        }

        assertThat(notifier.findRecentAlerts(ALERT_HISTORY_MAX_LIMIT + 10_000)).hasSize(3)
        assertThat(notifier.findRecentAlerts(0)).hasSize(1)
    }
}
