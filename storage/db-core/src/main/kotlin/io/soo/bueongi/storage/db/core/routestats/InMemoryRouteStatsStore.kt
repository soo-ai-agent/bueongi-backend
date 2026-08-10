package io.soo.bueongi.storage.db.core.routestats

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InMemoryRouteStatsStore : RouteStatsStore {

    private val logger: Logger = LoggerFactory.getLogger(InMemoryRouteStatsStore::class.java)
    private val logs: ArrayDeque<RouteRecommendationLog> = ArrayDeque<RouteRecommendationLog>()
    private val lock: Any = Any()

    override fun record(log: RouteRecommendationLog) {
        try {
            synchronized(lock) {
                logs.addFirst(log)
                while (logs.size > ROUTE_STATS_CAP) {
                    logs.removeLast()
                }
            }
        } catch (e: Exception) {
            logger.warn("추천 통계 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(limit: Int): List<RouteRecommendationLog> {
        val snapshot: List<RouteRecommendationLog> = synchronized(lock) { logs.toList() }
        return snapshot.take(limit.coerceAtLeast(0))
    }
}
