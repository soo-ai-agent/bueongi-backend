package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InMemoryRouteChoiceStoreTest {
    private fun choice(at: Long) =
        RouteChoiceLog(
            atEpochMs = at,
            chosenType = RouteType.SAFE,
            candidateCount = 3,
            score = 82,
            distanceMeters = 1200,
            durationSeconds = 900,
            preference = SafetyPreference.SAFE,
            destinationName = "강남역",
        )

    @Test
    fun `이 저장소의 상한은 ROUTE_CHOICE_CAP 이다`() {
        val store = InMemoryRouteChoiceStore()
        val total: Long = ROUTE_CHOICE_CAP + 50L
        (1L..total).forEach { store.record(choice(it)) }

        val all: List<RouteChoiceLog> = store.findRecent(ROUTE_CHOICE_CAP + 100)

        assertEquals(ROUTE_CHOICE_CAP, all.size)
        assertEquals(total, all.first().atEpochMs)
    }

    @Test
    fun `추천 기록을 상한 넘게 쌓아도 선택 기록은 그대로 남는다`() {
        val store = InMemoryRouteChoiceStore()
        val recommendations = InMemoryRouteStatsStore()
        store.record(choice(1))

        repeat(ROUTE_STATS_CAP + 100) {
            recommendations.record(
                RouteRecommendationLog(
                    atEpochMs = it.toLong(),
                    endpoint = "safe-compare",
                    destinationName = "강남역",
                    preference = SafetyPreference.SAFE,
                ),
            )
        }

        assertEquals(1, store.findRecent(ROUTE_CHOICE_CAP).size)
        assertEquals(1L, store.findRecent(1).first().atEpochMs)
    }
}
