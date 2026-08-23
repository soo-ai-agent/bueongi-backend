package io.soo.bueongi.storage.db.core.routestats

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InMemoryRouteChoiceStore : RouteChoiceStore {

    private val logger: Logger = LoggerFactory.getLogger(InMemoryRouteChoiceStore::class.java)
    private val logs: ArrayDeque<RouteChoiceLog> = ArrayDeque<RouteChoiceLog>()
    private val lock = Any()

    override fun record(log: RouteChoiceLog) {
        try {
            synchronized(lock) {
                logs.addFirst(log)
                while (logs.size > ROUTE_CHOICE_CAP) {
                    logs.removeLast()
                }
            }
        } catch (e: Exception) {
            logger.warn("경로 선택 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(limit: Int): List<RouteChoiceLog> {
        val snapshot: List<RouteChoiceLog> = synchronized(lock) { logs.toList() }
        return snapshot.take(limit.coerceAtLeast(0))
    }
}
