package io.soo.bueongi.storage.db.core.facility

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class SpatialMathTest {

    @Test
    fun `pathLengthMeters - 2점 미만이면 0`() {
        assertThat(SpatialMath.pathLengthMeters(emptyList())).isEqualTo(0.0)
        assertThat(SpatialMath.pathLengthMeters(listOf(GeoPoint(37.5, 127.0)))).isEqualTo(0.0)
    }

    @Test
    fun `pathLengthMeters - 직선 경로 길이는 구간 합`() {
        val path = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.501, 127.0), GeoPoint(37.502, 127.0))
        val expected = SpatialMath.haversineMeters(GeoPoint(37.5, 127.0), GeoPoint(37.502, 127.0))
        assertThat(SpatialMath.pathLengthMeters(path)).isCloseTo(expected, within(0.5))
    }

    @Test
    fun `distancePointToSegment - 선분 위 점은 0에 가깝다`() {
        val a: GeoPoint = GeoPoint(37.5, 127.0)
        val b: GeoPoint = GeoPoint(37.5, 127.01)
        val mid: GeoPoint = GeoPoint(37.5, 127.005)
        assertThat(SpatialMath.distancePointToSegmentMeters(mid, a, b)).isLessThan(1.0)
    }

    @Test
    fun `distancePointToSegment - 수직 이탈 거리(약 111m)`() {
        val a: GeoPoint = GeoPoint(37.5, 127.0)
        val b: GeoPoint = GeoPoint(37.5, 127.01)
        val off: GeoPoint = GeoPoint(37.501, 127.005)
        assertThat(SpatialMath.distancePointToSegmentMeters(off, a, b)).isCloseTo(111.0, within(5.0))
    }

    @Test
    fun `distancePointToSegment - 퇴화 선분은 점-점 거리`() {
        val a: GeoPoint = GeoPoint(37.5, 127.0)
        val p: GeoPoint = GeoPoint(37.501, 127.0)
        assertThat(SpatialMath.distancePointToSegmentMeters(p, a, a))
            .isCloseTo(SpatialMath.haversineMeters(GeoPoint(37.5, 127.0), GeoPoint(37.501, 127.0)), within(1.0))
    }

    @Test
    fun `projectOntoSegment - 중점은 t 약 0_5`() {
        val a: GeoPoint = GeoPoint(37.5, 127.0)
        val b: GeoPoint = GeoPoint(37.5, 127.01)
        val mid: GeoPoint = GeoPoint(37.5, 127.005)
        val (t, _) = SpatialMath.projectOntoSegment(mid, a, b)
        assertThat(t).isCloseTo(0.5, within(0.02))
    }

    @Test
    fun `projectOntoSegment - 선분 너머는 t 1 초과(클램프 전)`() {
        val a: GeoPoint = GeoPoint(37.5, 127.0)
        val b: GeoPoint = GeoPoint(37.5, 127.01)
        val beyond: GeoPoint = GeoPoint(37.5, 127.02)
        val (t, _) = SpatialMath.projectOntoSegment(beyond, a, b)
        assertThat(t).isGreaterThan(1.0)
    }

    @Test
    fun `minDistanceToPath - 빈 경로는 무한대, 단일 점은 점-점 거리`() {
        val p: GeoPoint = GeoPoint(37.5, 127.0)
        assertThat(SpatialMath.minDistanceToPathMeters(p, emptyList())).isInfinite()
        assertThat(SpatialMath.minDistanceToPathMeters(p, listOf(GeoPoint(37.501, 127.0))))
            .isCloseTo(SpatialMath.haversineMeters(GeoPoint(37.5, 127.0), GeoPoint(37.501, 127.0)), within(1.0))
    }

    @Test
    fun `overlapLengthMeters - 동일 경로는 거의 전체가 겹친다`() {
        val path = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.01), GeoPoint(37.5, 127.02))
        val overlap = SpatialMath.overlapLengthMeters(path, path, 30.0)
        val total = SpatialMath.pathLengthMeters(path)
        assertThat(overlap).isCloseTo(total, within(1.0))
    }

    @Test
    fun `overlapLengthMeters - 멀리 떨어진 경로는 0`() {
        val base = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.01))
        val far = listOf(GeoPoint(38.0, 128.0), GeoPoint(38.0, 128.01))
        assertThat(SpatialMath.overlapLengthMeters(base, far, 30.0)).isEqualTo(0.0)
    }

    @Test
    fun `overlapLengthMeters - 2점 미만이면 0`() {
        val single = listOf(GeoPoint(37.5, 127.0))
        val path = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.01))
        assertThat(SpatialMath.overlapLengthMeters(single, path, 30.0)).isEqualTo(0.0)
        assertThat(SpatialMath.overlapLengthMeters(path, single, 30.0)).isEqualTo(0.0)
    }
}
