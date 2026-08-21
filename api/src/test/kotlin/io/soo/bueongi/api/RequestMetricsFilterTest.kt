package io.soo.bueongi.api

import io.soo.bueongi.api.config.RequestMetricsFilter

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import org.slf4j.LoggerFactory
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RequestMetricsFilterTest {

    private class CapturingStore : RequestMetricStore {
        val recorded: MutableList<RequestMetric> = mutableListOf<RequestMetric>()
        override fun record(metric: RequestMetric) {
            recorded += metric
        }
        override fun findRecent(limit: Int): List<RequestMetric> = recorded.take(limit)
    }

    private fun chainSetting(status: Int) = FilterChain { _, response ->
        (response as HttpServletResponse).status = status
    }

    @Test
    fun `체인 실행 후 메서드 정규화경로 clientId status 를 기록한다`() {
        val store = CapturingStore()
        val request = MockHttpServletRequest("POST", "/share/abc123/location")
        request.addHeader("X-Client-Id", "anon-42")
        val response = MockHttpServletResponse()

        RequestMetricsFilter(store).doFilter(request, response, chainSetting(200))

        val metric: RequestMetric = store.recorded.single()
        assertThat(metric.method).isEqualTo("POST")
        assertThat(metric.endpoint).isEqualTo("/share/{token}/location")
        assertThat(metric.clientId).isEqualTo("anon-42")
        assertThat(metric.status).isEqualTo(200)
        assertThat(metric.durationMs).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `X-Client-Id 가 없거나 공백이면 clientId 는 null 이다`() {
        val store = CapturingStore()
        val missing = MockHttpServletRequest("GET", "/api/places/search")
        val blank = MockHttpServletRequest("GET", "/api/places/search").apply { addHeader("X-Client-Id", "   ") }

        RequestMetricsFilter(store).doFilter(missing, MockHttpServletResponse(), chainSetting(200))
        RequestMetricsFilter(store).doFilter(blank, MockHttpServletResponse(), chainSetting(200))

        assertThat(store.recorded.map { it.clientId }).containsExactly(null, null)
    }

    @Test
    fun `실패 status 도 그대로 기록한다`() {
        val store = CapturingStore()
        val request = MockHttpServletRequest("GET", "/api/places/search")
        request.addHeader("X-Client-Id", "anon-1")

        RequestMetricsFilter(store).doFilter(request, MockHttpServletResponse(), chainSetting(500))

        assertThat(store.recorded.single().status).isEqualTo(500)
    }

    @Test
    fun `관리자와 운영 경로는 계측하지 않는다`() {
        val store = CapturingStore()
        val filter = RequestMetricsFilter(store)
        val excluded: List<String> = listOf("/api/admin/traffic", "/actuator/health", "/healthz", "/cdn/safetySpots.json")

        excluded.forEach { path ->
            filter.doFilter(MockHttpServletRequest("GET", path), MockHttpServletResponse(), chainSetting(200))
        }

        assertThat(store.recorded).isEmpty()
    }

    private fun captureLogs(): ListAppender<ILoggingEvent> {
        val logger: LogbackLogger = LoggerFactory.getLogger(RequestMetricsFilter::class.java) as LogbackLogger
        val appender: ListAppender<ILoggingEvent> = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        return appender
    }

    @Test
    fun `요청 한 줄을 남긴다 - 공유 토큰은 가리고 질의 문자열은 안 남긴다`() {
        val appender = captureLogs()
        val request = MockHttpServletRequest("POST", "/share/abc123/location")
        request.queryString = "lat=37.5&lng=127.0"

        RequestMetricsFilter(CapturingStore()).doFilter(request, MockHttpServletResponse(), chainSetting(200))

        val line: String = appender.list.single().formattedMessage
        assertThat(line).contains("POST", "/share/{token}/location", "200")
        assertThat(line).doesNotContain("abc123")
        assertThat(line).doesNotContain("37.5")
    }

    @Test
    fun `서버 오류는 warn 으로 올린다`() {
        val appender = captureLogs()
        val request = MockHttpServletRequest("GET", "/api/routes/safe-compare")

        RequestMetricsFilter(CapturingStore()).doFilter(request, MockHttpServletResponse(), chainSetting(500))

        val event: ILoggingEvent = appender.list.single()
        assertThat(event.level).isEqualTo(Level.WARN)
        assertThat(event.formattedMessage).contains("500")
    }
}
