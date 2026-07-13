package io.soo.bueongi.api.config

import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * [RequestMetricsFilter] 단위 테스트 — 체인 실행 후 계측이 기록되는지, 관리자·운영 경로가 제외되는지,
 * `X-Client-Id` 가 캡처되고(없으면 null) 경로가 정규화되는지 고정한다. 저장소는 기록을 모으는 페이크로 대체한다.
 */
class RequestMetricsFilterTest {

    private class CapturingStore : RequestMetricStore {
        val recorded = mutableListOf<RequestMetric>()
        override fun record(metric: RequestMetric) {
            recorded += metric
        }
        override fun findRecent(limit: Int): List<RequestMetric> = recorded.take(limit)
    }

    /** 다운스트림이 status 를 세팅하는 것을 흉내낸 체인. */
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

        val metric = store.recorded.single()
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
        val excluded = listOf("/api/admin/traffic", "/actuator/health", "/healthz", "/cdn/facilities.json")

        excluded.forEach { path ->
            filter.doFilter(MockHttpServletRequest("GET", path), MockHttpServletResponse(), chainSetting(200))
        }

        assertThat(store.recorded).isEmpty()
    }
}
