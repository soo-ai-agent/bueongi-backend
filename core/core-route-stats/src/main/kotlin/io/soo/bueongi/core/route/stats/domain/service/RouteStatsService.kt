package io.soo.bueongi.core.route.stats.domain.service

import io.soo.bueongi.core.route.stats.domain.model.DestinationCount
import io.soo.bueongi.core.route.stats.domain.model.PreferenceCount
import io.soo.bueongi.core.route.stats.domain.model.RouteStatsSummary
import io.soo.bueongi.core.route.stats.domain.model.EndpointCount
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.support.util.HourlyBucket
import io.soo.bueongi.support.util.countByHour

import io.soo.bueongi.storage.db.core.routestats.ROUTE_STATS_CAP
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service
import java.util.EnumMap

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
        val counts: MutableMap<String, Int> = HashMap()
        for (log in logs) {
            counts[log.endpoint] = (counts[log.endpoint] ?: 0) + 1
        }
        val rows: ArrayList<EndpointCount> = ArrayList()
        for (endpoint in TRACKED_ENDPOINTS) {
            rows.add(EndpointCount(endpoint = endpoint, count = counts[endpoint] ?: 0))
        }
        return rows
    }

    private fun byPreference(logs: List<RouteRecommendationLog>): List<PreferenceCount> {
        val counts: MutableMap<SafetyPreference, Int> = EnumMap(SafetyPreference::class.java)
        for (log in logs) {
            counts[log.preference] = (counts[log.preference] ?: 0) + 1
        }
        val rows: ArrayList<PreferenceCount> = ArrayList()
        for (preference in SafetyPreference.entries) {
            rows.add(PreferenceCount(preference = preference, count = counts[preference] ?: 0))
        }
        return rows
    }

    private fun topDestinations(logs: List<RouteRecommendationLog>): List<DestinationCount> {
        val counts: HashMap<String, Int> = HashMap()
        for (log in logs) {
            val name: String = log.destinationName?.trim().orEmpty()
            if (name.isEmpty()) {
                continue
            }
            counts[name] = (counts[name] ?: 0) + 1
        }
        val rows: ArrayList<DestinationCount> = ArrayList()
        for (entry in counts.entries) {
            rows.add(DestinationCount(name = entry.key, count = entry.value))
        }
        rows.sortByDescending { it.count }
        if (rows.size <= TOP_DESTINATIONS_LIMIT) {
            return rows
        }
        return rows.subList(0, TOP_DESTINATIONS_LIMIT)
    }

    private fun hourlyTrend(logs: List<RouteRecommendationLog>): List<HourlyBucket> {
        val timestamps: ArrayList<Long> = ArrayList()
        for (log in logs) {
            timestamps.add(log.atEpochMs)
        }
        return countByHour(timestamps)
    }

    companion object {
        val TRACKED_ENDPOINTS: List<String> = listOf("compare", "safe-compare")
        const val TOP_DESTINATIONS_LIMIT: Int = 10
    }
}
