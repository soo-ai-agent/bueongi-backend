package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.storage.db.core.InMemoryRecentLog

class InMemoryRouteStatsStore : RouteStatsStore {
    private val recent: InMemoryRecentLog<RouteRecommendationLog> = InMemoryRecentLog(ROUTE_STATS_CAP)

    override fun record(log: RouteRecommendationLog) {
        recent.push(log)
    }

    override fun findRecent(limit: Int): List<RouteRecommendationLog> = recent.readRecent(limit)
}
