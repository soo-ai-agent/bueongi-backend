package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.SafetyPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [InMemoryRouteStatsStore] 단위 테스트 — 저장소 계약([RouteStatsStore])의 핵심 불변식을 고정한다:
 * 최근순(최신 먼저) 반환, 상한([ROUTE_STATS_CAP]) 초과 시 오래된 것 축출, limit 절삭, 빈 조회.
 */
class InMemoryRouteStatsStoreTest {

    private val store: InMemoryRouteStatsStore = InMemoryRouteStatsStore()

    private fun log(at: Long, endpoint: String = "compare", preference: SafetyPreference = SafetyPreference.SAFE) =
        RouteRecommendationLog(atEpochMs = at, endpoint = endpoint, destinationName = "목적지", preference = preference)

    @Test
    fun `record 후 findRecent 는 최신이 먼저다`() {
        store.record(log(1))
        store.record(log(2))
        store.record(log(3))

        val recent: List<RouteRecommendationLog> = store.findRecent(10)

        assertEquals(listOf(3L, 2L, 1L), recent.map { it.atEpochMs })
    }

    @Test
    fun `limit 만큼만 반환한다(최신부터)`() {
        (1L..5L).forEach { store.record(log(it)) }

        assertEquals(listOf(5L, 4L), store.findRecent(2).map { it.atEpochMs })
    }

    @Test
    fun `상한을 초과하면 가장 오래된 것부터 축출된다`() {
        val total: Long = ROUTE_STATS_CAP + 50L
        (1L..total).forEach { store.record(log(it)) }

        val all: List<RouteRecommendationLog> = store.findRecent(ROUTE_STATS_CAP + 100)

        assertEquals(ROUTE_STATS_CAP, all.size)
        assertEquals(total, all.first().atEpochMs)
        assertEquals(51L, all.last().atEpochMs)
    }

    @Test
    fun `기록이 없으면 빈 목록`() {
        assertTrue(store.findRecent(10).isEmpty())
    }
}
