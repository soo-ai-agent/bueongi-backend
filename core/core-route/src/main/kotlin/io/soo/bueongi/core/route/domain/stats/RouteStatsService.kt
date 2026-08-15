package io.soo.bueongi.core.route.domain.stats

import io.soo.bueongi.core.common.stats.EndpointCount
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.support.util.HourlyBucket
import io.soo.bueongi.support.util.HourlyBuckets

import io.soo.bueongi.storage.db.core.routestats.ROUTE_STATS_CAP
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

@Service
class RouteStatsService(
    private val routeStatsStore: RouteStatsStore,
) {

    fun summarize(): RouteStatsSummary {
        val logs: List<RouteRecommendationLog> = routeStatsStore.findRecent(ROUTE_STATS_CAP)
        return RouteStatsSummary(
            total = logs.size,
            windowSize = logs.size,
            byEndpoint = byEndpoint(logs),
            byPreference = byPreference(logs),
            topDestinations = topDestinations(logs),
            hourlyTrend = hourlyTrend(logs),
        )
    }

    /** 추적 대상 엔드포인트를 고정 순서로 모두 낸다 — 0건도 대시보드에 자리를 지킨다. */
    private fun byEndpoint(logs: List<RouteRecommendationLog>): List<EndpointCount> {
        val counts: Map<String, Int> = logs.groupingBy { it.endpoint }.eachCount()
        return TRACKED_ENDPOINTS.map { endpoint ->
            EndpointCount(endpoint = endpoint, count = counts[endpoint] ?: 0)
        }
    }

    /** [byEndpoint] 와 같은 규칙 — 안심강도 전 종류를 enum 순서로 내고 없는 값은 0. */
    private fun byPreference(logs: List<RouteRecommendationLog>): List<PreferenceCount> {
        val counts: Map<SafetyPreference, Int> = logs.groupingBy { it.preference }.eachCount()
        return SafetyPreference.entries.map { preference ->
            PreferenceCount(preference = preference, count = counts[preference] ?: 0)
        }
    }

    private fun topDestinations(logs: List<RouteRecommendationLog>): List<DestinationCount> =
        logs.asSequence()
            .mapNotNull { it.destinationName?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(TOP_DESTINATIONS_LIMIT)
            .map { DestinationCount(name = it.key, count = it.value) }

    private fun hourlyTrend(logs: List<RouteRecommendationLog>): List<HourlyBucket> =
        HourlyBuckets.countOf(logs.map { it.atEpochMs })

    companion object {
        val TRACKED_ENDPOINTS: List<String> = listOf("compare", "safe-compare")

        const val TOP_DESTINATIONS_LIMIT: Int = 10
    }
}
