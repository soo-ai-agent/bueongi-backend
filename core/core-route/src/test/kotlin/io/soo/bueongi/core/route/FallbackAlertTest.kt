package io.soo.bueongi.core.route

import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.common.request.LatLng
import io.soo.bueongi.core.common.request.NamedLatLng
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.safety.service.SafeWaypointFinder
import io.soo.bueongi.core.route.domain.service.RouteFinder
import io.soo.bueongi.core.route.domain.service.RouteCandidateMerger
import io.soo.bueongi.core.route.domain.service.RouteEventPublisher
import io.soo.bueongi.core.route.domain.service.RouteOptionAssembler
import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.core.route.domain.service.SafeRouteService
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import io.soo.bueongi.core.route.domain.error.RouteSearchUnavailableException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher

class FallbackAlertTest {
    private class RecordingPublisher : ApplicationEventPublisher {
        val events: MutableList<OperationEvent> = ArrayList()

        override fun publishEvent(event: Any) {
            if (event is OperationEvent) {
                events.add(event)
            }
        }
    }

    private val source = FakeSafetySpotSource()

    private val query =
        RouteRequest(
            rawOrigin = LatLng(37.5, 127.0),
            rawDestination = NamedLatLng(37.5, 127.01, name = "도착"),
            rawRouteType = "safe",
            rawSafetyPreference = "safe",
        )

    private fun service(
        tmap: TmapRouteClient,
        publisher: RecordingPublisher,
    ): SafeRouteService =
        SafeRouteService(
            routeFinder = RouteFinder(tmap, SafeWaypointFinder(source)),
            candidateMerger = RouteCandidateMerger(),
            scorer = nightScorer(source),
            assembler = RouteOptionAssembler(RouteMarkerBuilder(source, mock(), mock()), source, RouteTypeCatalog()),
            routeEvents = RouteEventPublisher(publisher),
        )

    @Test
    fun `Tmap 후보가 전멸하면 503 으로 알리면서 알림을 쏜다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetchRoute(any(), any(), any(), any(), eq(emptyList()))).thenReturn(null)
        val notifier = RecordingPublisher()

        assertThatThrownBy { service(tmap, notifier).safeCompare(query) }
            .isInstanceOf(RouteSearchUnavailableException::class.java)

        assertThat(notifier.events).singleElement()
            .isEqualTo(OperationEvent("tmap-route-fallback", "Tmap 경로 API 실패", "모든 경로 후보 호출 실패 — 503 으로 알림"))
    }

    @Test
    fun `Tmap 키가 없으면 장애가 아니므로 알리지 않는다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(false)
        val notifier = RecordingPublisher()

        assertThatThrownBy { service(tmap, notifier).safeCompare(query) }
            .isInstanceOf(RouteSearchUnavailableException::class.java)

        assertThat(notifier.events).isEmpty()
    }
}
