package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [RequestMetricsService] 집계 정확성 — 저장된 수신 요청 기록만으로 엔드포인트별 호출수·성공률·평균 소요·
 * 고유 클라이언트, 예측 사용자 수(today/7d/30d + clientId 없는 호출), 시간대/일자 추이를 계산한다.
 * 저장소는 넘겨준 기록을 그대로 돌려주는 페이크(가장 얇은 대역)로 대체한다.
 */
class RequestMetricsServiceTest {

    private fun store(metrics: List<RequestMetric>): RequestMetricStore = object : RequestMetricStore {
        override fun record(metric: RequestMetric) = Unit
        override fun findRecent(limit: Int): List<RequestMetric> = metrics.take(limit)
    }

    private fun metric(
        at: Long = System.currentTimeMillis(),
        method: String = "GET",
        endpoint: String = "/api/places/search",
        clientId: String? = "anon-1",
        status: Int = 200,
        durationMs: Long = 10,
    ) = RequestMetric(
        atEpochMs = at,
        method = method,
        endpoint = endpoint,
        clientId = clientId,
        status = status,
        durationMs = durationMs,
    )

    @Test
    fun `byEndpoint 는 메서드+경로별로 호출수 성공률 평균 고유클라이언트를 집계하고 호출수 내림차순이다`() {
        val metrics = listOf(
            metric(endpoint = "/api/places/search", clientId = "a", status = 200, durationMs = 100),
            metric(endpoint = "/api/places/search", clientId = "b", status = 500, durationMs = 200),
            metric(endpoint = "/api/places/search", clientId = "a", status = 200, durationMs = 300),
            metric(method = "POST", endpoint = "/api/routes/compare", clientId = "c", status = 200, durationMs = 50),
        )

        val byEndpoint = RequestMetricsService(store(metrics)).summarize().byEndpoint

        assertThat(byEndpoint.map { it.count }).isSortedAccordingTo(reverseOrder()) // 내림차순
        val search = byEndpoint.first()
        assertThat(search.method).isEqualTo("GET")
        assertThat(search.endpoint).isEqualTo("/api/places/search")
        assertThat(search.count).isEqualTo(3)
        assertThat(search.successRate).isEqualTo(2.0 / 3.0) // 3건 중 2xx 2건
        assertThat(search.avgDurationMs).isEqualTo(200L) // (100+200+300)/3
        assertThat(search.distinctClients).isEqualTo(2) // a, b
    }

    @Test
    fun `같은 경로라도 메서드가 다르면 별도 집계한다`() {
        val metrics = listOf(
            metric(method = "GET", endpoint = "/share/{token}/route"),
            metric(method = "POST", endpoint = "/share/{token}/route"),
        )

        val byEndpoint = RequestMetricsService(store(metrics)).summarize().byEndpoint

        assertThat(byEndpoint).hasSize(2)
        assertThat(byEndpoint.map { it.method }).containsExactlyInAnyOrder("GET", "POST")
    }

    @Test
    fun `정규화된 share 토큰 경로는 하나의 엔드포인트로 묶인다`() {
        val metrics = listOf(
            metric(method = "POST", endpoint = "/share/{token}/location", clientId = "a"),
            metric(method = "POST", endpoint = "/share/{token}/location", clientId = "b"),
            metric(method = "POST", endpoint = "/share/{token}/location", clientId = "a"),
        )

        val byEndpoint = RequestMetricsService(store(metrics)).summarize().byEndpoint

        assertThat(byEndpoint).hasSize(1)
        assertThat(byEndpoint.single().endpoint).isEqualTo("/share/{token}/location")
        assertThat(byEndpoint.single().count).isEqualTo(3)
        assertThat(byEndpoint.single().distinctClients).isEqualTo(2)
    }

    @Test
    fun `successRate 는 2xx 와 3xx 를 성공으로 4xx 5xx 를 실패로 본다`() {
        val metrics = listOf(
            metric(status = 200),
            metric(status = 301),
            metric(status = 404),
            metric(status = 500),
        )

        val endpoint = RequestMetricsService(store(metrics)).summarize().byEndpoint.single()

        assertThat(endpoint.count).isEqualTo(4)
        assertThat(endpoint.successRate).isEqualTo(0.5) // 200,301 성공 / 404,500 실패
    }

    @Test
    fun `estimatedUsers 는 고유 clientId 를 today 7d 30d 로 집계하고 null 호출은 제외 후 별도 노출한다`() {
        val now = System.currentTimeMillis()
        val metrics = listOf(
            metric(at = now, clientId = "a"),
            metric(at = now, clientId = "b"),
            metric(at = now, clientId = "a"), // 중복 a
            metric(at = now, clientId = null), // clientId 없음 → 추정 제외
            metric(at = now - 10 * DAY_MS, clientId = "c"), // 30일 안·7일 밖
            metric(at = now - 40 * DAY_MS, clientId = "d"), // 30일 밖
        )

        val estimated = RequestMetricsService(store(metrics)).summarize().estimatedUsers

        assertThat(estimated.today).isEqualTo(2) // a, b
        assertThat(estimated.last7d).isEqualTo(2) // a, b
        assertThat(estimated.last30d).isEqualTo(3) // a, b, c
        assertThat(estimated.callsWithoutClientId).isEqualTo(1)
    }

    @Test
    fun `byEndpoint 는 그룹 내 소요시간 p95 를 함께 집계한다`() {
        val metrics = (1..20).map { metric(durationMs = (it * 10).toLong()) } // 10..200 (한 엔드포인트)

        val endpoint = RequestMetricsService(store(metrics)).summarize().byEndpoint.single()

        assertThat(endpoint.avgDurationMs).isEqualTo(105L) // (10+…+200)/20
        assertThat(endpoint.p95DurationMs).isEqualTo(190L) // ceil(0.95*20)=19 → 19번째 표본
    }

    @Test
    fun `latency 는 전체 소요시간의 p50 p95 p99 분위수다`() {
        val metrics = listOf(
            metric(durationMs = 10),
            metric(durationMs = 20),
            metric(durationMs = 30),
            metric(durationMs = 40),
            metric(durationMs = 50),
        )

        val latency = RequestMetricsService(store(metrics)).summarize().latency

        // 5개 정렬 [10,20,30,40,50]: p50→3번째(30), p95·p99→5번째(50)
        assertThat(latency.p50Ms).isEqualTo(30L)
        assertThat(latency.p95Ms).isEqualTo(50L)
        assertThat(latency.p99Ms).isEqualTo(50L)
    }

    @Test
    fun `newToday 는 창 내 최초관측이 오늘인 사용자 returningToday 는 오늘 이전부터 본 사용자다`() {
        val now = System.currentTimeMillis()
        val metrics = listOf(
            metric(at = now, clientId = "a"), // 오늘 처음 관측 → 신규
            metric(at = now - 2 * DAY_MS, clientId = "b"), // b 최초=이틀 전
            metric(at = now, clientId = "b"), // b 오늘도 활동 → 재방문
            metric(at = now - 2 * DAY_MS, clientId = "c"), // 오늘 활동 없음 → today 대상 아님
            metric(at = now, clientId = null), // clientId 없음 → 제외
        )

        val estimated = RequestMetricsService(store(metrics)).summarize().estimatedUsers

        assertThat(estimated.today).isEqualTo(2) // a, b
        assertThat(estimated.newToday).isEqualTo(1) // a
        assertThat(estimated.returningToday).isEqualTo(1) // b
    }

    @Test
    fun `hourlyErrors 는 24버킷이며 2xx 3xx 가 아닌 요청만 세고 hourlyCalls 와 같은 경계다`() {
        val now = System.currentTimeMillis()
        val metrics = listOf(
            metric(at = now, status = 200), // 성공
            metric(at = now, status = 301), // 3xx 성공
            metric(at = now, status = 404), // 에러
            metric(at = now, status = 500), // 에러
            metric(at = now - 25 * HOUR_MS, status = 500), // 창 밖 → 제외
        )

        val summary = RequestMetricsService(store(metrics)).summarize()

        assertThat(summary.hourlyErrors).hasSize(24)
        assertThat(summary.hourlyErrors.map { it.hourStartMs }).isSorted()
        assertThat(summary.hourlyErrors.sumOf { it.count }).isEqualTo(2) // 404, 500 (창 안)
        assertThat(summary.hourlyCalls.sumOf { it.count }).isEqualTo(4) // 같은 경계, 창 안 4건
    }

    @Test
    fun `hourlyCalls 는 24버킷 오래된→최신이며 창 밖 호출은 제외한다`() {
        val now = System.currentTimeMillis()
        val metrics = listOf(
            metric(at = now),
            metric(at = now - 25 * HOUR_MS), // 24시간 창 밖
        )

        val trend = RequestMetricsService(store(metrics)).summarize().hourlyCalls

        assertThat(trend).hasSize(24)
        assertThat(trend.map { it.hourStartMs }).isSorted()
        assertThat(trend.sumOf { it.count }).isEqualTo(1) // 창 안 1건만
    }

    @Test
    fun `dailyActiveUsers 는 7일 오래된→최신이며 각 날 고유 사용자 수다`() {
        val now = System.currentTimeMillis()
        val metrics = listOf(
            metric(at = now, clientId = "a"),
            metric(at = now, clientId = "b"),
            metric(at = now, clientId = "a"), // 중복
            metric(at = now - 40 * DAY_MS, clientId = "z"), // 7일 창 밖
        )

        val daily = RequestMetricsService(store(metrics)).summarize().dailyActiveUsers

        assertThat(daily).hasSize(7)
        assertThat(daily.map { it.dateEpochMs }).isSorted() // 오래된→최신
        assertThat(daily.last().distinctClients).isEqualTo(2) // 오늘 a, b
        assertThat(daily.dropLast(1).sumOf { it.distinctClients }).isEqualTo(0) // 나머지 날은 0
    }

    @Test
    fun `빈 기록이면 총계 0 엔드포인트 빈 예측 0 시간대 24개 0 일자 7개 0`() {
        val summary = RequestMetricsService(store(emptyList())).summarize()

        assertThat(summary.totalCalls).isEqualTo(0)
        assertThat(summary.windowSize).isEqualTo(0)
        assertThat(summary.byEndpoint).isEmpty()
        assertThat(summary.estimatedUsers.today).isEqualTo(0)
        assertThat(summary.estimatedUsers.last7d).isEqualTo(0)
        assertThat(summary.estimatedUsers.last30d).isEqualTo(0)
        assertThat(summary.estimatedUsers.callsWithoutClientId).isEqualTo(0)
        assertThat(summary.estimatedUsers.newToday).isEqualTo(0)
        assertThat(summary.estimatedUsers.returningToday).isEqualTo(0)
        assertThat(summary.latency.p50Ms).isEqualTo(0L)
        assertThat(summary.latency.p95Ms).isEqualTo(0L)
        assertThat(summary.latency.p99Ms).isEqualTo(0L)
        assertThat(summary.hourlyCalls).hasSize(24)
        assertThat(summary.hourlyCalls.sumOf { it.count }).isEqualTo(0)
        assertThat(summary.hourlyErrors).hasSize(24)
        assertThat(summary.hourlyErrors.sumOf { it.count }).isEqualTo(0)
        assertThat(summary.dailyActiveUsers).hasSize(7)
        assertThat(summary.dailyActiveUsers.sumOf { it.distinctClients }).isEqualTo(0)
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
    }
}
