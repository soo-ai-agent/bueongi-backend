package io.soo.bueongi.core.admin.route.stats.domain.service

import io.soo.bueongi.core.admin.route.stats.api.response.RouteStatsResponse
import io.soo.bueongi.storage.db.core.routestats.ROUTE_STATS_CAP
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

@Service
class RouteStatsService(
    private val routeStatsStore: RouteStatsStore,
) {
    fun summarize(): RouteStatsResponse {
        val logs: List<RouteRecommendationLog> = routeStatsStore.findRecent(ROUTE_STATS_CAP)
        return RouteStatsResponse.from(logs)
    }
}
