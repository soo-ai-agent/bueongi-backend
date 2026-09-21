package io.soo.bueongi.core.route.domain.marker

import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoiType
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 마커 상한(종류당 12개)을 경로 앞에서부터 채우면 출발지 쪽에서 예산을 다 쓴다.
 * 실측(강남역→홍대입구 13.4km)에서 가로등 12개가 전부 첫 1/3 구간, 가로 150m 안에 몰렸고
 * 목적지 쪽은 0개였다. 경로 전체에 고르게 펴져야 한다.
 */
class RouteMarkerSpreadTest {

    private val origin = GeoPoint(37.5, 127.000)
    private val destination = GeoPoint(37.5, 127.200)

    /** 출발지에서 목적지까지 경도만 늘려 [count] 개를 고르게 깐다. */
    private fun lampsAlongPath(count: Int): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(count)
        for (index in 0 until count) {
            val lng: Double = origin.lng + (destination.lng - origin.lng) * index / (count - 1)
            points.add(SafetySpotPoint(SafetySpotType.STREET_LAMP, origin.lat, lng))
        }
        return points
    }

    private fun lampMarkerLngs(spotCount: Int): List<Double> {
        val source = FakeSafetySpotSource(mapOf(SafetySpotType.STREET_LAMP to lampsAlongPath(spotCount)))
        val path: List<GeoPoint> = listOf(origin, destination)
        return RouteMarkerBuilder(source, null)
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

        // 앞에서부터 자르면 목적지 쪽이 0이 된다 — 세 구간 모두 마커가 있어야 한다.
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

    /** 상한보다 적으면 솎아낼 이유가 없다 — 있는 대로 다 보여 준다. */
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
