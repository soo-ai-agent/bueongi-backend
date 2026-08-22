package io.soo.bueongi.core.route.domain.marker

import io.soo.bueongi.core.common.event.RouteVisitedEvent
import io.soo.bueongi.core.common.event.VisitedPoint
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.marker.service.RouteVisitEventPublisher
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

/**
 * 지연 적재 사건이 경로 **전체**를 가리키는지 본다.
 *
 * 보안등은 자치단체(instt_code)당 한 번 받는 원천이라, 출발·목적지 두 점만 발행하면
 * 강남→마포처럼 여러 자치단체를 지나는 경로에서 서초·용산·중구가 영원히 안 채워진다.
 * 동시에 표본이 늘면 외부 호출도 느는데, 점 하나가 최악의 경우 자치단체 하나(약 8회 호출)라
 * 상한이 없으면 긴 경로 한 건이 쿼터를 크게 먹는다.
 */
class RouteVisitSampleTest {

    /** [RouteMarkerBuilder] 의 표본 상한과 같은 값 — 계약이라 테스트에도 적어 둔다. */
    private val maxSamplePoints: Int = 12

    private class RecordingPublisher : ApplicationEventPublisher {
        val events: MutableList<Any> = ArrayList()
        override fun publishEvent(event: Any) {
            events.add(event)
        }
    }

    private fun visitedPoints(path: List<GeoPoint>): List<GeoPoint> {
        val recorder = RecordingPublisher()
        RouteMarkerBuilder(FakeSafetySpotSource(), null, RouteVisitEventPublisher(recorder))
            .buildMarkers(path.first(), path.last(), path)
        val event: RouteVisitedEvent = recorder.events.single() as RouteVisitedEvent
        val points: ArrayList<GeoPoint> = ArrayList(event.points.size)
        for (point: VisitedPoint in event.points) {
            points.add(GeoPoint(lat = point.lat, lng = point.lng))
        }
        return points
    }

    /** 위도를 고정하고 경도만 늘린 곧은 경로. 위도 37.5 에서 경도 1도는 약 88.2km 다. */
    private fun straightPath(lngSpanDegrees: Double, vertexCount: Int): List<GeoPoint> {
        val points: ArrayList<GeoPoint> = ArrayList(vertexCount)
        for (index in 0 until vertexCount) {
            points.add(GeoPoint(37.5, 127.0 + lngSpanDegrees * index / (vertexCount - 1)))
        }
        return points
    }

    private fun gapsMeters(points: List<GeoPoint>): List<Double> {
        val gaps: ArrayList<Double> = ArrayList(points.size - 1)
        for (index in 1 until points.size) {
            gaps.add(points[index - 1].distanceMetersTo(points[index]))
        }
        return gaps
    }

    /** 두 점짜리 직선 경로(`/api/routes/safety-spots`)에도 중간이 나와야 한다 — 보간해서 만든다. */
    @Test
    fun `직선 두 점 경로도 약 2km 간격으로 중간 지점을 함께 발행한다`() {
        val path: List<GeoPoint> = straightPath(lngSpanDegrees = 0.1, vertexCount = 2)

        val visited: List<GeoPoint> = visitedPoints(path)

        assertThat(visited).hasSize(6)
        assertThat(visited.first()).isEqualTo(path.first())
        assertThat(visited.last()).isEqualTo(path.last())
        assertThat(gapsMeters(visited).dropLast(1)).allSatisfy { assertThat(it).isCloseTo(2_000.0, within(1.0)) }
    }

    /** 꼭짓점이 많은 실제 경로(Tmap)도 마찬가지다. 중간 구간이 비면 그 자치단체를 못 받는다. */
    @Test
    fun `꼭짓점이 많은 경로는 중간 구간에도 표본이 놓인다`() {
        val path: List<GeoPoint> = straightPath(lngSpanDegrees = 0.15, vertexCount = 300)

        val visited: List<GeoPoint> = visitedPoints(path)

        val span: Double = path.last().lng - path.first().lng
        val middle: List<GeoPoint> = visited.filter {
            it.lng > path.first().lng + span / 3 && it.lng < path.first().lng + span * 2 / 3
        }
        assertThat(middle).isNotEmpty()
        assertThat(visited.last()).isEqualTo(path.last())
    }

    /**
     * 상한에 걸려도 뒤를 자르지 않는다 — 자르면 목적지 쪽 자치단체가 다시 영원히 빠진다.
     * 간격을 넓혀 상한 개수로 끝까지 덮는다.
     */
    @Test
    fun `아주 긴 경로는 표본 상한을 넘지 않으면서 목적지까지 덮는다`() {
        val path: List<GeoPoint> = straightPath(lngSpanDegrees = 1.13, vertexCount = 2)

        val visited: List<GeoPoint> = visitedPoints(path)

        assertThat(visited).hasSize(maxSamplePoints)
        assertThat(visited.last()).isEqualTo(path.last())
        // 간격을 안 넓히고 앞에서부터 2km 씩만 찍으면 뒤에 80km 짜리 구멍이 남는다.
        assertThat(gapsMeters(visited).max()).isLessThan(10_000.0)
    }

    @Test
    fun `표본 간격보다 짧은 경로는 출발·목적지만 발행한다`() {
        val path: List<GeoPoint> = straightPath(lngSpanDegrees = 0.005, vertexCount = 2)

        assertThat(visitedPoints(path)).isEqualTo(path)
    }

    @Test
    fun `사건을 받을 쪽이 없으면(null) 마커 생성은 그대로 동작한다`() {
        val markers = RouteMarkerBuilder(FakeSafetySpotSource(), null, null)
            .buildMarkers(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.1), listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.1)))

        assertThat(markers).hasSize(2)
    }
}
