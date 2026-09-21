package io.soo.bueongi.storage.db.core.adevent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InMemoryAdEventStoreTest {

    @Test
    fun `이 저장소의 상한은 AD_EVENT_CAP 이다`() {
        val store = InMemoryAdEventStore()
        val total: Long = AD_EVENT_CAP + 50L
        (1L..total).forEach { store.record(AdEvent(atEpochMs = it, placement = "interstitial")) }

        val all: List<AdEvent> = store.findRecent(AD_EVENT_CAP + 100)

        assertEquals(AD_EVENT_CAP, all.size)
        assertEquals(total, all.first().atEpochMs)
    }
}
