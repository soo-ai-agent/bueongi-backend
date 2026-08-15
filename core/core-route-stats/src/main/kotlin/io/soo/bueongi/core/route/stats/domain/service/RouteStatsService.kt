package io.soo.bueongi.core.route.stats.domain.service

import io.soo.bueongi.core.route.stats.domain.model.DestinationCount
import io.soo.bueongi.core.route.stats.domain.model.PreferenceCount
import io.soo.bueongi.core.route.stats.domain.model.RouteStatsSummary
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
        // 최근 것만 상한까지 읽 어 그 자리에서 집계한다(누적 집계를 따로 저장하지 않는다).
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
        val counts: MutableMap<String, Int> = HashMap<String, Int>()
        for (log in logs) {
            counts[log.endpoint] = (counts[log.endpoint] ?: 0) + 1
        }
        val rows: ArrayList<EndpointCount> = ArrayList<EndpointCount>()
        for (endpoint in TRACKED_ENDPOINTS) {
            rows.add(EndpointCount(endpoint = endpoint, count = counts[endpoint] ?: 0))
        }
        return rows
    }

    /** [byEndpoint] 와 같은 규칙 — 안심강도 전 종류를 enum 순서로 내고 없는 값은 0. */
    private fun byPreference(logs: List<RouteRecommendationLog>): List<PreferenceCount> {
        val counts: MutableMap<SafetyPreference, Int> = HashMap<SafetyPreference, Int>()
        for (log in logs) {
            counts[log.preference] = (counts[log.preference] ?: 0) + 1
        }
        val rows: ArrayList<PreferenceCount> = ArrayList<PreferenceCount>()
        for (preference in SafetyPreference.entries) {
            rows.add(PreferenceCount(preference = preference, count = counts[preference] ?: 0))
        }
        return rows
    }

    /** 이름 없는 목적지(좌표만 찍은 경우)는 셀 수 없으니 뺀다. */
    private fun topDestinations(logs: List<RouteRecommendationLog>): List<DestinationCount> {
        val counts: MutableMap<String, Int> = HashMap<String, Int>()
        for (log in logs) {
            val name: String = log.destinationName?.trim().orEmpty()
            if (name.isEmpty()) {
                continue
            }
            counts[name] = (counts[name] ?: 0) + 1
        }
        val rows: ArrayList<DestinationCount> = ArrayList<DestinationCount>()
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
        val timestamps: ArrayList<Long> = ArrayList<Long>()
        for (log in logs) {
            timestamps.add(log.atEpochMs)
        }
        return HourlyBuckets.countOf(timestamps)
    }

    companion object {
        val TRACKED_ENDPOINTS: List<String> = listOf("compare", "safe-compare")

        const val TOP_DESTINATIONS_LIMIT: Int = 10
    }
}
