package io.soo.bueongi.storage.db.core.adevent

import io.soo.bueongi.storage.db.core.InMemoryRecentLog

class InMemoryAdEventStore : AdEventStore {
    private val recent: InMemoryRecentLog<AdEvent> = InMemoryRecentLog(AD_EVENT_CAP)

    override fun record(event: AdEvent) {
        recent.push(event)
    }

    override fun findRecent(limit: Int): List<AdEvent> = recent.readRecent(limit)
}
