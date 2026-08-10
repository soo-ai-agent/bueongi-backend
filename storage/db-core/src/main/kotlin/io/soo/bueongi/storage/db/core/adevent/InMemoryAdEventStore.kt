package io.soo.bueongi.storage.db.core.adevent

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InMemoryAdEventStore : AdEventStore {

    private val logger: Logger = LoggerFactory.getLogger(InMemoryAdEventStore::class.java)
    private val events: ArrayDeque<AdEvent> = ArrayDeque<AdEvent>()
    private val lock: Any = Any()

    override fun record(event: AdEvent) {
        try {
            synchronized(lock) {
                events.addFirst(event)
                while (events.size > AD_EVENT_CAP) {
                    events.removeLast()
                }
            }
        } catch (e: Exception) {
            logger.warn("광고 노출 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(limit: Int): List<AdEvent> {
        val snapshot: List<AdEvent> = synchronized(lock) { events.toList() }
        return snapshot.take(limit.coerceAtLeast(0))
    }
}
