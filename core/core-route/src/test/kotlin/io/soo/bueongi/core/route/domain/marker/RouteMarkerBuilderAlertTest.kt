package io.soo.bueongi.core.route.domain.marker

import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoiType
import io.soo.bueongi.core.route.domain.marker.service.MarkerEventPublisher
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class RouteMarkerBuilderAlertTest {
    private val origin = GeoPoint(37.5, 127.000)
    private val destination = GeoPoint(37.5, 127.020)

    private class RecordingPublisher : ApplicationEventPublisher {
        val events: MutableList<Any> = ArrayList()

        override fun publishEvent(event: Any) {
            events.add(event)
        }
    }

    private fun build(
        source: FakeSafetySpotSource,
        events: MarkerEventPublisher?,
    ) = RouteMarkerBuilder(source, events).buildMarkers(origin, destination, listOf(origin, destination))

    @Test
    fun `장애 - 시설 인덱스가 통째로 비면(총 적재 0) 데이터 제공 실패로 알린다`() {
        val notifier = RecordingPublisher()

        val markers = build(FakeSafetySpotSource(), MarkerEventPublisher(notifier))

        assertThat(markers.map { it.type }).containsExactlyInAnyOrder(SafetySpotPoiType.START, SafetySpotPoiType.END)
        assertThat(notifier.events).singleElement()
            .satisfies({ assertThat((it as OperationEvent).key).isEqualTo("safetySpot-index-empty") })
    }

    @Test
    fun `정상 - 시설이 적재됐고 회랑에만 없으면(적재량 GT 0, 회랑 0) 알리지 않는다`() {
        val notifier = RecordingPublisher()
        val farAway =
            FakeSafetySpotSource(
                mapOf(SafetySpotType.CCTV to listOf(SafetySpotPoint(SafetySpotType.CCTV, 38.0, 128.0))),
            )

        val markers = build(farAway, MarkerEventPublisher(notifier))

        assertThat(markers.map { it.type }).containsExactlyInAnyOrder(SafetySpotPoiType.START, SafetySpotPoiType.END)
        assertThat(notifier.events).isEmpty()
    }

    @Test
    fun `스로틀 - 빈 인덱스로 반복 호출해도 항상 같은 상수 key 를 써서 폭주가 억제된다`() {
        val notifier = RecordingPublisher()
        val source = FakeSafetySpotSource()

        repeat(3) { build(source, MarkerEventPublisher(notifier)) }

        assertThat(notifier.events.map { (it as OperationEvent).key }.toSet())
            .containsExactly("safetySpot-index-empty")
    }
}
