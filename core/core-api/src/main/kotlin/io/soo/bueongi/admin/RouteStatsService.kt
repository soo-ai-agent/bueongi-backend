package io.soo.bueongi.admin

import io.soo.bueongi.core.enums.SafetyPreference
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

    private fun byEndpoint(logs: List<RouteRecommendationLog>): List<EndpointCount> {
        val counts: Map<String, Int> = logs.groupingBy { it.endpoint }.eachCount()
        return TRACKED_ENDPOINTS.map { endpoint ->
            EndpointCount(endpoint = endpoint, count = counts[endpoint] ?: 0)
        }
    }

    private fun byPreference(logs: List<RouteRecommendationLog>): List<PreferenceCount> =
        logs.groupingBy { it.preference }.eachCount()
            .map { (preference, count) -> PreferenceCount(preference = preference, count = count) }
            .sortedByDescending { it.count }

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
