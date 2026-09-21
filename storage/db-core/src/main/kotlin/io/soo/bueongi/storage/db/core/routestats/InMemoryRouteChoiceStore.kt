package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.storage.db.core.InMemoryRecentLog

class InMemoryRouteChoiceStore : RouteChoiceStore {

    private val recent: InMemoryRecentLog<RouteChoiceLog> = InMemoryRecentLog(ROUTE_CHOICE_CAP)

    override fun record(log: RouteChoiceLog) {
        recent.push(log)
    }

    override fun findRecent(limit: Int): List<RouteChoiceLog> =
        recent.readRecent(limit)
}
