package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 병합 기준이 후보 수를 정하고, 후보 수가 곧 사용자가 고를 수 있는 길의 수다.
 * 실측(2026-08-23)에서 25m·0.85 는 강남역→홍대입구 후보 6개를 2개로, 신촌역→이대역 3개를 1개로 줄였다.
 */
class RouteCandidateMergerTest {

    private val merger = RouteCandidateMerger()

    @Test
    fun `똑같은 길은 하나로 합친다`() {
        val kept = merger.mergeNearDuplicates(listOf(candidate("0", straight(SEGMENTS)), candidate("4", straight(SEGMENTS))))

        assertThat(kept).hasSize(1)
        assertThat(kept.first().searchOption).describedAs("먼저 온 후보를 남긴다").isEqualTo("0")
    }

    /**
     * 서울 도심 이면도로 간격이 대략 20m 다. 회랑을 25m 로 재면 나란한 두 골목이 같은 길로 셈되는데,
     * 조명도 CCTV 도 골목마다 다르니 같은 길이 아니다.
     */
    @Test
    fun `20m 옆 나란한 골목은 다른 길이다`() {
        val kept = merger.mergeNearDuplicates(
            listOf(candidate("0", straight(SEGMENTS)), candidate("4", straight(SEGMENTS, latOffset = TWENTY_METERS_IN_DEGREES))),
        )

        assertThat(kept).hasSize(2)
    }

    /** 88% 만 겹치면 나머지 12%(약 265m)를 다른 길로 걷는다 — 지도에 다른 선이 그려진다. */
    @Test
    fun `88퍼센트 겹치는 길은 따로 남긴다`() {
        val kept = merger.mergeNearDuplicates(
            listOf(candidate("0", straight(SEGMENTS)), candidate("4", divergingAfter(22))),
        )

        assertThat(kept).hasSize(2)
    }

    /** 96% 겹치면 한 블록 차이라 화면에서 같은 길로 보인다. */
    @Test
    fun `96퍼센트 겹치는 길은 합친다`() {
        val kept = merger.mergeNearDuplicates(
            listOf(candidate("0", straight(SEGMENTS)), candidate("4", divergingAfter(24))),
        )

        assertThat(kept).hasSize(1)
    }

    @Test
    fun `길이가 0 인 후보는 병합 판단에서 제외된다`() {
        val degenerate = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.0))
        val kept = merger.mergeNearDuplicates(listOf(candidate("0", straight(SEGMENTS)), candidate("4", degenerate)))

        assertThat(kept).hasSize(2)
    }

    private fun candidate(searchOption: String, path: List<GeoPoint>): RouteCandidate =
        RouteCandidate(searchOption = searchOption, path = path, distanceM = 1.0, timeS = 1, steps = emptyList())

    private fun straight(segments: Int, latOffset: Double = 0.0): List<GeoPoint> {
        val points: MutableList<GeoPoint> = ArrayList()
        for (i in 0..segments) {
            points.add(GeoPoint(BASE_LAT + latOffset, BASE_LNG + i * SEGMENT_DEGREES))
        }
        return points
    }

    /** [sharedSegments] 개까지는 같은 길을 걷고 그 뒤로 300m 북쪽으로 갈라진다. */
    private fun divergingAfter(sharedSegments: Int): List<GeoPoint> {
        val points: MutableList<GeoPoint> = ArrayList()
        for (i in 0..SEGMENTS) {
            val lat: Double = if (i <= sharedSegments) {
                BASE_LAT
            } else {
                BASE_LAT + FAR_AWAY_DEGREES
            }
            points.add(GeoPoint(lat, BASE_LNG + i * SEGMENT_DEGREES))
        }
        return points
    }

    private companion object {

        const val BASE_LAT: Double = 37.5
        const val BASE_LNG: Double = 127.0

        /** 한 구간 약 88m — 갈라지는 구간의 중점이 회랑 밖에 확실히 놓이도록 짧지 않게 잡는다. */
        const val SEGMENT_DEGREES: Double = 0.001

        /** 25개 구간 = 약 2.2km. 겹침 비율을 구간 수로 정확히 만들려고 등간격으로 둔다. */
        const val SEGMENTS: Int = 25

        const val TWENTY_METERS_IN_DEGREES: Double = 0.00018

        const val FAR_AWAY_DEGREES: Double = 0.003
    }
}
