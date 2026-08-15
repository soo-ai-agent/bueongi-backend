package io.soo.bueongi.core.domain.admin

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteStatsServiceTest {

    private fun store(logs: List<RouteRecommendationLog>): RouteStatsStore = object : RouteStatsStore {
        override fun record(log: RouteRecommendationLog) = Unit
        override fun findRecent(limit: Int): List<RouteRecommendationLog> = logs.take(limit)
    }

    private fun log(
        at: Long = System.currentTimeMillis(),
        endpoint: String = "compare",
        destinationName: String? = "강남역",
        preference: SafetyPreference = SafetyPreference.SAFE,
    ) = RouteRecommendationLog(atEpochMs = at, endpoint = endpoint, destinationName = destinationName, preference = preference)

    @Test
    fun `byEndpoint 는 compare safe-compare 2종 고정이고 없는 엔드포인트는 0 이다`() {
        val logs = listOf(
            log(endpoint = "compare"),
            log(endpoint = "compare"),
            log(endpoint = "safe-compare"),
        )

        val summary = RouteStatsService(store(logs)).summarize()

        assertThat(summary.total).isEqualTo(3)
        assertThat(summary.windowSize).isEqualTo(3)
        assertThat(summary.byEndpoint.map { it.endpoint }).containsExactly("compare", "safe-compare")
        assertThat(summary.byEndpoint.first { it.endpoint == "compare" }.count).isEqualTo(2)
        assertThat(summary.byEndpoint.first { it.endpoint == "safe-compare" }.count).isEqualTo(1)
    }

    @Test
    fun `byPreference 는 나타난 값만 많은 순으로 집계한다`() {
        val logs = List(3) { log(preference = SafetyPreference.SAFE) } +
            List(2) { log(preference = SafetyPreference.BALANCED) } +
            List(1) { log(preference = SafetyPreference.SAFEST) }

        val byPreference = RouteStatsService(store(logs)).summarize().byPreference

        assertThat(byPreference.map { it.preference }).containsExactly(SafetyPreference.SAFE, SafetyPreference.BALANCED, SafetyPreference.SAFEST)
        assertThat(byPreference.map { it.count }).containsExactly(3, 2, 1)
    }

    @Test
    fun `topDestinations 는 빈 이름 제외 많은 순 최대 10건이다`() {
        val busy = List(3) { log(destinationName = "강남역") } +
            List(1) { log(destinationName = "홍대입구역") } +
            List(2) { log(destinationName = null) } +
            List(1) { log(destinationName = "  ") } +
            (1..12).map { log(destinationName = "목적지$it") }

        val top = RouteStatsService(store(busy)).summarize().topDestinations

        assertThat(top).hasSize(10)
        assertThat(top.first().name).isEqualTo("강남역")
        assertThat(top.first().count).isEqualTo(3)
        assertThat(top.map { it.name }).doesNotContain("  ")
        assertThat(top.map { it.count }).isSortedAccordingTo(reverseOrder())
    }

    @Test
    fun `hourlyTrend 는 24개 버킷이고 오래된→최신 순이며 창 밖 요청은 제외한다`() {
        val now = System.currentTimeMillis()
        val logs = listOf(
            log(at = now),
            log(at = now - 25 * HOUR_MS),
        )

        val trend = RouteStatsService(store(logs)).summarize().hourlyTrend

        assertThat(trend).hasSize(24)
        assertThat(trend.map { it.hourStartMs }).isSorted()
        assertThat(trend.sumOf { it.count }).isEqualTo(1)
    }

    @Test
    fun `빈 로그면 total 0 엔드포인트 2종 0 선호·목적지 빈 트렌드 24개 0`() {
        val summary = RouteStatsService(store(emptyList())).summarize()

        assertThat(summary.total).isEqualTo(0)
        assertThat(summary.windowSize).isEqualTo(0)
        assertThat(summary.byEndpoint.map { it.endpoint }).containsExactly("compare", "safe-compare")
        assertThat(summary.byEndpoint.map { it.count }).containsExactly(0, 0)
        assertThat(summary.byPreference).isEmpty()
        assertThat(summary.topDestinations).isEmpty()
        assertThat(summary.hourlyTrend).hasSize(24)
        assertThat(summary.hourlyTrend.sumOf { it.count }).isEqualTo(0)
    }

    private companion object {
        const val HOUR_MS: Long = 3_600_000L
    }
}
