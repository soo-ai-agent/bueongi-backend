package io.soo.bueongi.core.route.marker

import io.soo.bueongi.core.route.marker.service.MarkerEventPublisher
import io.soo.bueongi.core.common.event.FacilityIndexEmpty
import org.springframework.context.ApplicationEventPublisher
import io.soo.bueongi.core.route.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.enums.FacilityPoiType

import io.soo.bueongi.storage.db.core.facility.store.FakeFacilitySource


import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.model.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.model.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteMarkerBuilderAlertTest {

    private val origin: GeoPoint = GeoPoint(37.5, 127.000)
    private val destination: GeoPoint = GeoPoint(37.5, 127.020)

    private class RecordingPublisher : ApplicationEventPublisher {
        val events: MutableList<Any> = ArrayList()
        override fun publishEvent(event: Any) {
            events.add(event)
        }
    }

    private fun build(source: FakeFacilitySource, events: MarkerEventPublisher?) =
        RouteMarkerBuilder(source, events).buildMarkers(origin, destination, listOf(origin, destination))

    @Test
    fun `장애 - 시설 인덱스가 통째로 비면(총 적재 0) 데이터 제공 실패로 알린다`() {
        val notifier: RecordingPublisher = RecordingPublisher()

        val markers = build(FakeFacilitySource(), MarkerEventPublisher(notifier))

        assertThat(markers.map { it.type }).containsExactlyInAnyOrder(FacilityPoiType.START, FacilityPoiType.END)
        assertThat(notifier.events).singleElement().isInstanceOf(FacilityIndexEmpty::class.java)
    }

    @Test
    fun `정상 - 시설이 적재됐고 회랑에만 없으면(적재량 GT 0, 회랑 0) 알리지 않는다`() {
        val notifier: RecordingPublisher = RecordingPublisher()
        val farAway = FakeFacilitySource(
            mapOf(FacilityType.CCTV to listOf(FacilityPoint(FacilityType.CCTV, 38.0, 128.0))),
        )

        val markers = build(farAway, MarkerEventPublisher(notifier))

        assertThat(markers.map { it.type }).containsExactlyInAnyOrder(FacilityPoiType.START, FacilityPoiType.END)
        assertThat(notifier.events).isEmpty()
    }

    @Test
    fun `스로틀 - 빈 인덱스로 반복 호출해도 항상 같은 상수 key 를 써서 폭주가 억제된다`() {
        val notifier: RecordingPublisher = RecordingPublisher()
        val source: FakeFacilitySource = FakeFacilitySource()

        repeat(3) { build(source, MarkerEventPublisher(notifier)) }

        assertThat(notifier.events).allMatch { it is FacilityIndexEmpty }
        assertThat(notifier.events.map { (it as FacilityIndexEmpty).key }.toSet())
            .containsExactly("facility-index-empty")
    }

    @Test
    fun `알림기 미주입(null)이어도 마커 생성은 정상 동작한다`() {
        val markers = build(FakeFacilitySource(), events = null)
        assertThat(markers.map { it.type }).containsExactlyInAnyOrder(FacilityPoiType.START, FacilityPoiType.END)
    }
}
