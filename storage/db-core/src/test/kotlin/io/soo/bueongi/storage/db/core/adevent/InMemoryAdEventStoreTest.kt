package io.soo.bueongi.storage.db.core.adevent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [InMemoryAdEventStore] 단위 테스트 — 저장소 계약([AdEventStore])의 핵심 불변식을 고정한다:
 * 최근순(최신 먼저) 반환, 상한([AD_EVENT_CAP]) 초과 시 오래된 것 축출, limit 절삭, 빈 조회.
 */
class InMemoryAdEventStoreTest {

    private val store: InMemoryAdEventStore = InMemoryAdEventStore()

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
        assertEquals(total, all.first().atEpochMs) // 최신 유지
        assertEquals(51L, all.last().atEpochMs) // 1..50 은 축출됨
    }

    @Test
    fun `기록이 없으면 빈 목록`() {
        assertTrue(store.findRecent(10).isEmpty())
    }
}
