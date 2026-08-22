package io.soo.bueongi.core.ops.domain.traffic

import io.soo.bueongi.core.ops.domain.traffic.service.RequestMetricsService

import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.AdminRequestMetricStore
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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

    private fun adminStore(metrics: List<RequestMetric> = emptyList()): AdminRequestMetricStore =
        object : AdminRequestMetricStore {
            override fun record(metric: RequestMetric) = Unit
            override fun findRecent(limit: Int): List<RequestMetric> = metrics.take(limit)
        }

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-08-22T03:00:00Z"), ZoneOffset.UTC)

    /** 사용자 창만 보는 시험 — 관리자 창은 비워 둔다. */
    private fun serviceOf(metrics: List<RequestMetric>): RequestMetricsService =
        RequestMetricsService(store(metrics), adminStore(), fixedClock)

    @Test
    fun `byEndpoint 는 메서드+경로별로 호출수 성공률 평균 고유클라이언트를 집계하고 호출수 내림차순이다`() {
        val metrics = listOf(
            metric(endpoint = "/api/places/search", clientId = "a", status = 200, durationMs = 100),
            metric(endpoint = "/api/places/search", clientId = "b", status = 500, durationMs = 200),
            metric(endpoint = "/api/places/search", clientId = "a", status = 200, durationMs = 300),
            metric(method = "POST", endpoint = "/api/routes/compare", clientId = "c", status = 200, durationMs = 50),
        )

        val byEndpoint = serviceOf(metrics).summarize().byEndpoint

        assertThat(byEndpoint.map { it.count }).isSortedAccordingTo(reverseOrder())
        val search = byEndpoint.first()
        assertThat(search.method).isEqualTo("GET")
        assertThat(search.endpoint).isEqualTo("/api/places/search")
        assertThat(search.count).isEqualTo(3)
        assertThat(search.successRate).isEqualTo(2.0 / 3.0)
        assertThat(search.avgDurationMs).isEqualTo(200L)
        assertThat(search.distinctClients).isEqualTo(2)
    }

    @Test
    fun `같은 경로라도 메서드가 다르면 별도 집계한다`() {
        val metrics = listOf(
            metric(method = "GET", endpoint = "/share/{token}/route"),
            metric(method = "POST", endpoint = "/share/{token}/route"),
        )

        val byEndpoint = serviceOf(metrics).summarize().byEndpoint

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

        val byEndpoint = serviceOf(metrics).summarize().byEndpoint

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

        val endpoint = serviceOf(metrics).summarize().byEndpoint.single()

        assertThat(endpoint.count).isEqualTo(4)
        assertThat(endpoint.successRate).isEqualTo(0.5)
    }

    @Test
    fun `estimatedUsers 는 고유 clientId 를 today 7d 30d 로 집계하고 null 호출은 제외 후 별도 노출한다`() {
        val now = System.currentTimeMillis()
        val metrics = listOf(
            metric(at = now, clientId = "a"),
            metric(at = now, clientId = "b"),
            metric(at = now, clientId = "a"),
            metric(at = now, clientId = null),
            metric(at = now - 10 * DAY_MS, clientId = "c"),
            metric(at = now - 40 * DAY_MS, clientId = "d"),
        )

        val estimated = serviceOf(metrics).summarize().estimatedUsers

        assertThat(estimated.today).isEqualTo(2)
        assertThat(estimated.last7d).isEqualTo(2)
        assertThat(estimated.last30d).isEqualTo(3)
        assertThat(estimated.callsWithoutClientId).isEqualTo(1)
    }

    @Test
    fun `byEndpoint 는 그룹 내 소요시간 p95 를 함께 집계한다`() {
        val metrics = (1..20).map { metric(durationMs = (it * 10).toLong()) }

        val endpoint = serviceOf(metrics).summarize().byEndpoint.single()

        assertThat(endpoint.avgDurationMs).isEqualTo(105L)
        assertThat(endpoint.p95DurationMs).isEqualTo(190L)
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

        val latency = serviceOf(metrics).summarize().latency

        assertThat(latency.p50Ms).isEqualTo(30L)
        assertThat(latency.p95Ms).isEqualTo(50L)
        assertThat(latency.p99Ms).isEqualTo(50L)
    }

    @Test
    fun `newToday 는 창 내 최초관측이 오늘인 사용자 returningToday 는 오늘 이전부터 본 사용자다`() {
        val now = System.currentTimeMillis()
        val metrics = listOf(
            metric(at = now, clientId = "a"),
            metric(at = now - 2 * DAY_MS, clientId = "b"),
            metric(at = now, clientId = "b"),
            metric(at = now - 2 * DAY_MS, clientId = "c"),
            metric(at = now, clientId = null),
        )

        val estimated = serviceOf(metrics).summarize().estimatedUsers

        assertThat(estimated.today).isEqualTo(2)
        assertThat(estimated.newToday).isEqualTo(1)
        assertThat(estimated.returningToday).isEqualTo(1)
    }

    @Test
    fun `hourlyErrors 는 24버킷이며 2xx 3xx 가 아닌 요청만 세고 hourlyCalls 와 같은 경계다`() {
        val now = System.currentTimeMillis()
        val metrics = listOf(
            metric(at = now, status = 200),
            metric(at = now, status = 301),
            metric(at = now, status = 404),
            metric(at = now, status = 500),
            metric(at = now - 25 * HOUR_MS, status = 500),
        )

        val summary = serviceOf(metrics).summarize()

        assertThat(summary.hourlyErrors).hasSize(24)
        assertThat(summary.hourlyErrors.map { it.hourStartMs }).isSorted()
        assertThat(summary.hourlyErrors.sumOf { it.count }).isEqualTo(2)
        assertThat(summary.hourlyCalls.sumOf { it.count }).isEqualTo(4)
    }

    @Test
    fun `hourlyCalls 는 24버킷 오래된→최신이며 창 밖 호출은 제외한다`() {
        val now = System.currentTimeMillis()
        val metrics = listOf(
            metric(at = now),
            metric(at = now - 25 * HOUR_MS),
        )

        val trend = serviceOf(metrics).summarize().hourlyCalls

        assertThat(trend).hasSize(24)
        assertThat(trend.map { it.hourStartMs }).isSorted()
        assertThat(trend.sumOf { it.count }).isEqualTo(1)
    }

    @Test
    fun `dailyActiveUsers 는 7일 오래된→최신이며 각 날 고유 사용자 수다`() {
        val now = System.currentTimeMillis()
        val metrics = listOf(
            metric(at = now, clientId = "a"),
            metric(at = now, clientId = "b"),
            metric(at = now, clientId = "a"),
            metric(at = now - 40 * DAY_MS, clientId = "z"),
        )

        val daily = serviceOf(metrics).summarize().dailyActiveUsers

        assertThat(daily).hasSize(7)
        assertThat(daily.map { it.dateEpochMs }).isSorted()
        assertThat(daily.last().distinctClients).isEqualTo(2)
        assertThat(daily.dropLast(1).sumOf { it.distinctClients }).isEqualTo(0)
    }

    @Test
    fun `빈 기록이면 총계 0 엔드포인트 빈 예측 0 시간대 24개 0 일자 7개 0`() {
        val summary = serviceOf(emptyList()).summarize()

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
        const val HOUR_MS: Long = 3_600_000L
        const val DAY_MS: Long = 86_400_000L
    }

    /**
     * 관리자 미리보기는 5초마다 폴링한다 — 사용자 표에 섞이면 호출량·사용자 수가 부풀어 보인다.
     * 버리지도 않는다: 누가 무엇을 얼마나 열람했는지는 따로 볼 수 있어야 한다.
     */
    @Test
    fun `관리자 호출은 사용자 집계에 섞이지 않고 따로 나온다`() {
        val userMetrics = listOf(metric(endpoint = "/api/places/search", clientId = "a"))
        val adminMetrics = listOf(
            metric(endpoint = "/api/admin/traffic", clientId = null),
            metric(endpoint = "/api/admin/traffic", clientId = null),
            metric(endpoint = "/api/admin/share-sessions/{token}/location", clientId = null),
        )

        val summary = RequestMetricsService(store(userMetrics), adminStore(adminMetrics), fixedClock)
            .summarize()

        assertThat(summary.totalCalls).isEqualTo(1)
        assertThat(summary.byEndpoint.map { it.endpoint }).containsExactly("/api/places/search")
        assertThat(summary.adminCalls.totalCalls).isEqualTo(3)
        assertThat(summary.adminCalls.byEndpoint.first().endpoint).isEqualTo("/api/admin/traffic")
        assertThat(summary.adminCalls.byEndpoint.first().count).isEqualTo(2)
    }

    @Test
    fun `관리자 호출이 없어도 사용자 집계는 그대로다`() {
        val summary = serviceOf(listOf(metric(endpoint = "/api/places/search", clientId = "a"))).summarize()

        assertThat(summary.totalCalls).isEqualTo(1)
        assertThat(summary.adminCalls.totalCalls).isZero()
        assertThat(summary.adminCalls.byEndpoint).isEmpty()
        assertThat(summary.adminCalls.hourlyCalls).hasSize(24)
    }

    /** 일일 한도가 아니라 "오늘 관리자가 얼마나 봤나"를 읽는 칸이다 — 어제 것이 섞이면 안 된다. */
    @Test
    fun `관리자 오늘 호출 수는 서비스 시간대의 자정부터 센다`() {
        val nowMs = Instant.parse("2026-08-22T03:00:00Z").toEpochMilli()
        val yesterdayMs = Instant.parse("2026-08-21T13:00:00Z").toEpochMilli()
        val summary = RequestMetricsService(
            store(emptyList()),
            adminStore(
                listOf(
                    metric(endpoint = "/api/admin/traffic", clientId = null, at = nowMs),
                    metric(endpoint = "/api/admin/traffic", clientId = null, at = yesterdayMs),
                ),
            ),
            fixedClock,
        ).summarize()

        assertThat(summary.adminCalls.totalCalls).isEqualTo(2)
        assertThat(summary.adminCalls.callsToday).isEqualTo(1)
    }
}
