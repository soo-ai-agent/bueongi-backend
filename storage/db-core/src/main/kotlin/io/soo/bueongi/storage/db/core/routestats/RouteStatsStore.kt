package io.soo.bueongi.storage.db.core.routestats

interface RouteStatsStore {
    fun record(log: RouteRecommendationLog)
    fun findRecent(limit: Int): List<RouteRecommendationLog>
}

const val ROUTE_STATS_CAP: Int = 2000
