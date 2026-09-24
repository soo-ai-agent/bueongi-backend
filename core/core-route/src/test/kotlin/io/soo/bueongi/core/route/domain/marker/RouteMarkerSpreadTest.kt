package io.soo.bueongi.core.route.domain.marker

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoiType
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteMarkerSpreadTest {
    private val origin = GeoPoint(37.5, 127.000)
    private val destination = GeoPoint(37.5, 127.200)

    private fun lampsAlongPath(count: Int): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(count)
        for (index in 0 until count) {
            val lng: Double = origin.lng + (destination.lng - origin.lng) * index / (count - 1)
            points.add(SafetySpotPoint.of(SafetySpotType.STREET_LAMP, origin.lat, lng))
        }
        return points
    }

    private fun lampMarkerLngs(spotCount: Int): List<Double> {
        val source = FakeSafetySpotSource(mapOf(SafetySpotType.STREET_LAMP to lampsAlongPath(spotCount)))
        val path: List<GeoPoint> = listOf(origin, destination)
        return RouteMarkerBuilder(source)
            .buildMarkers(origin, destination, path)
            .filter { it.type == SafetySpotPoiType.STREET_LAMP }
            .map { it.lng }
    }

    @Test
    fun `시설이 상한보다 많으면 경로 전체에 고르게 편다`() {
        val lngs: List<Double> = lampMarkerLngs(200)

        val span: Double = destination.lng - origin.lng
        val nearOrigin: Int = lngs.count { it < origin.lng + span / 3 }
        val nearMiddle: Int = lngs.count { it >= origin.lng + span / 3 && it < origin.lng + span * 2 / 3 }
        val nearDestination: Int = lngs.count { it >= origin.lng + span * 2 / 3 }

        assertThat(nearOrigin).isPositive()
        assertThat(nearMiddle).isPositive()
        assertThat(nearDestination).isPositive()
    }

    @Test
    fun `마지막 마커는 경로 뒤쪽에 놓인다`() {
        val lngs: List<Double> = lampMarkerLngs(200)

        val span: Double = destination.lng - origin.lng
        assertThat(lngs.max()).isGreaterThan(origin.lng + span / 2)
    }

    @Test
    fun `시설이 적으면 전부 그대로 보여 준다`() {
        assertThat(lampMarkerLngs(5)).hasSize(5)
    }

    @Test
    fun `고르게 고르면서 같은 시설을 두 번 담지 않는다`() {
        assertThat(lampMarkerLngs(200)).doesNotHaveDuplicates()
        assertThat(lampMarkerLngs(13)).doesNotHaveDuplicates()
    }
}
