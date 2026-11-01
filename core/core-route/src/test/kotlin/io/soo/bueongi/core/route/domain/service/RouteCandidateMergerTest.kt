package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteCandidateMergerTest {
    private val merger = RouteCandidateMerger()

    @Test
    fun `같은 identityKey(거리·좌표수)는 하나만 남긴다`() {
        val kept = merger.merge(listOf(route("0", straight(SEGMENTS)), route("0", straight(SEGMENTS))))

        assertThat(kept).hasSize(1)
    }

    @Test
    fun `똑같은 길은 하나로 합친다`() {
        val kept = merger.merge(listOf(route("0", straight(SEGMENTS)), route("4", straight(SEGMENTS))))

        assertThat(kept).hasSize(1)
        assertThat(kept.first().searchOption).describedAs("먼저 온 후보를 남긴다").isEqualTo("0")
    }

    @Test
    fun `20m 옆 나란한 골목은 다른 길이다`() {
        val kept =
            merger.merge(
                listOf(route("0", straight(SEGMENTS)), route("4", straight(SEGMENTS, latOffset = TWENTY_METERS_IN_DEGREES))),
            )

        assertThat(kept).hasSize(2)
    }

    @Test
    fun `88퍼센트 겹치는 길은 따로 남긴다`() {
        val kept =
            merger.merge(
                listOf(route("0", straight(SEGMENTS)), route("4", divergingAfter(22))),
            )

        assertThat(kept).hasSize(2)
    }

    @Test
    fun `96퍼센트 겹치는 길은 합친다`() {
        val kept =
            merger.merge(
                listOf(route("0", straight(SEGMENTS)), route("4", divergingAfter(24))),
            )

        assertThat(kept).hasSize(1)
    }

    @Test
    fun `길이가 0 인 후보는 병합 판단에서 제외된다`() {
        val degenerate = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.0))
        val kept = merger.merge(listOf(route("0", straight(SEGMENTS)), route("4", degenerate)))

        assertThat(kept).hasSize(2)
    }

    // searchOption 마다 거리를 다르게 둔다 — identityKey(거리 기반)가 겹쳐 근접 중복 판정 전에 걸러지지 않게.
    private fun route(searchOption: String, path: List<GeoPoint>): RouteCandidate =
        RouteCandidate(
            searchOption = searchOption,
            path = path,
            distanceMeters = (searchOption.toDouble() + 1) * 10,
            timeSeconds = 1,
            steps = emptyList(),
        )

    private fun straight(segments: Int, latOffset: Double = 0.0): List<GeoPoint> {
        val points: MutableList<GeoPoint> = ArrayList()
        for (i in 0..segments) {
            points.add(GeoPoint(BASE_LAT + latOffset, BASE_LNG + i * SEGMENT_DEGREES))
        }
        return points
    }

    private fun divergingAfter(sharedSegments: Int): List<GeoPoint> {
        val points: MutableList<GeoPoint> = ArrayList()
        for (i in 0..SEGMENTS) {
            val lat: Double =
                if (i <= sharedSegments) {
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

        const val SEGMENT_DEGREES: Double = 0.001

        const val SEGMENTS: Int = 25

        const val TWENTY_METERS_IN_DEGREES: Double = 0.00018

        const val FAR_AWAY_DEGREES: Double = 0.003
    }
}
