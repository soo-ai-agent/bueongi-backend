package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.SafetyPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InMemoryRouteStatsStoreTest {

    @Test
    fun `이 저장소의 상한은 ROUTE_STATS_CAP 이다`() {
        val store = InMemoryRouteStatsStore()
        val total: Long = ROUTE_STATS_CAP + 50L
        (1L..total).forEach {
            store.record(
                RouteRecommendationLog(
                    atEpochMs = it,
                    endpoint = "compare",
                    destinationName = "목적지",
                    preference = SafetyPreference.SAFE,
                ),
            )
        }

        val all: List<RouteRecommendationLog> = store.findRecent(ROUTE_STATS_CAP + 100)

        assertEquals(ROUTE_STATS_CAP, all.size)
        assertEquals(total, all.first().atEpochMs)
    }
}
