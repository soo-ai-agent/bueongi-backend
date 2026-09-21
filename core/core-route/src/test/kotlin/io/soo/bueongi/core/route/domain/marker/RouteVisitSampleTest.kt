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

class RouteVisitSampleTest {

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

    @Test
    fun `직선 두 점 경로도 약 2km 간격으로 중간 지점을 함께 발행한다`() {
        val path: List<GeoPoint> = straightPath(lngSpanDegrees = 0.1, vertexCount = 2)

        val visited: List<GeoPoint> = visitedPoints(path)

        assertThat(visited).hasSize(6)
        assertThat(visited.first()).isEqualTo(path.first())
        assertThat(visited.last()).isEqualTo(path.last())
        assertThat(gapsMeters(visited).dropLast(1)).allSatisfy { assertThat(it).isCloseTo(2_000.0, within(1.0)) }
    }

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

    @Test
    fun `아주 긴 경로는 표본 상한을 넘지 않으면서 목적지까지 덮는다`() {
        val path: List<GeoPoint> = straightPath(lngSpanDegrees = 1.13, vertexCount = 2)

        val visited: List<GeoPoint> = visitedPoints(path)

        assertThat(visited).hasSize(maxSamplePoints)
        assertThat(visited.last()).isEqualTo(path.last())
        assertThat(gapsMeters(visited).max())
            .describedAs("간격을 넓혀 덮으므로 중간에 큰 구멍이 없다")
            .isLessThan(10_000.0)
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
