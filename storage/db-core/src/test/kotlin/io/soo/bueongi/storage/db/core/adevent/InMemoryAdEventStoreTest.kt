package io.soo.bueongi.storage.db.core.adevent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryAdEventStoreTest {

    private val store = InMemoryAdEventStore()

    private fun event(at: Long, placement: String = "interstitial") =
        AdEvent(atEpochMs = at, placement = placement)

    @Test
    fun `record 후 findRecent 는 최신이 먼저다`() {
        store.record(event(1))
        store.record(event(2))
        store.record(event(3))

        assertEquals(listOf(3L, 2L, 1L), store.findRecent(10).map { it.atEpochMs })
    }

    @Test
    fun `limit 만큼만 반환한다(최신부터)`() {
        (1L..5L).forEach { store.record(event(it)) }

        assertEquals(listOf(5L, 4L), store.findRecent(2).map { it.atEpochMs })
    }

    @Test
    fun `상한을 초과하면 가장 오래된 것부터 축출된다`() {
        val total: Long = AD_EVENT_CAP + 50L
        (1L..total).forEach { store.record(event(it)) }

        val all: List<AdEvent> = store.findRecent(AD_EVENT_CAP + 100)

        assertEquals(AD_EVENT_CAP, all.size)
        assertEquals(total, all.first().atEpochMs)
        assertEquals(51L, all.last().atEpochMs)
    }

    @Test
    fun `기록이 없으면 빈 목록`() {
        assertTrue(store.findRecent(10).isEmpty())
    }
}
