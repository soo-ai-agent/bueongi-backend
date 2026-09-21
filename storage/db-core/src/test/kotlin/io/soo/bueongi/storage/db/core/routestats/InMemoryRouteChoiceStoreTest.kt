package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryRouteChoiceStoreTest {

    private val store = InMemoryRouteChoiceStore()

    private fun choice(at: Long, type: RouteType = RouteType.SAFE) = RouteChoiceLog(
        atEpochMs = at,
        chosenType = type,
        candidateCount = 3,
        score = 82,
        distanceMeters = 1200,
        durationSeconds = 900,
        preference = SafetyPreference.SAFE,
        destinationName = "강남역",
    )

    @Test
    fun `record 후 findRecent 는 최신이 먼저다`() {
        store.record(choice(1))
        store.record(choice(2))
        store.record(choice(3))

        assertEquals(listOf(3L, 2L, 1L), store.findRecent(10).map { it.atEpochMs })
    }

    @Test
    fun `상한을 초과하면 가장 오래된 것부터 축출된다`() {
        val total: Long = ROUTE_CHOICE_CAP + 50L
        (1L..total).forEach { store.record(choice(it)) }

        val all: List<RouteChoiceLog> = store.findRecent(ROUTE_CHOICE_CAP + 100)

        assertEquals(ROUTE_CHOICE_CAP, all.size)
        assertEquals(total, all.first().atEpochMs)
        assertEquals(51L, all.last().atEpochMs)
    }

    @Test
    fun `추천 기록을 상한 넘게 쌓아도 선택 기록은 그대로 남는다`() {
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

    @Test
    fun `기록이 없으면 빈 목록`() {
        assertTrue(store.findRecent(10).isEmpty())
    }
}
