package io.soo.bueongi.core.route

import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.common.request.LatLng
import io.soo.bueongi.core.common.request.NamedLatLng
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.safety.service.SafeWaypointFinder
import io.soo.bueongi.core.route.domain.service.RouteCandidateMerger
import io.soo.bueongi.core.route.domain.service.RouteFinder
import io.soo.bueongi.core.route.domain.service.RouteOptionAssembler
import io.soo.bueongi.core.route.domain.service.RouteTypeAssigner
import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.core.route.domain.service.SafeRouteService
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RerouteCallBudgetTest {
    private fun queryOf(reroute: Boolean): RouteRequest {
        return RouteRequest(
            rawOrigin = LatLng(37.5, 127.0),
            rawDestination = NamedLatLng(37.5, 127.01),
            rawRouteType = "safe",
            rawSafetyPreference = "safe",
            reroute = reroute,
        )
    }

    private val source =
        FakeSafetySpotSource(
            mapOf(
                SafetySpotType.CCTV to
                    listOf(127.002, 127.004, 127.006, 127.008).map {
                        SafetySpotPoint.of(SafetySpotType.CCTV, 37.5, it)
                    },
            ),
        )

    private val tmapRoutes =
        listOf(
            TmapRoute("0", listOf(TmapCoord(37.5, 127.0), TmapCoord(37.5, 127.01)), 880.0, 700, emptyList()),
            TmapRoute("10", listOf(TmapCoord(37.501, 127.0), TmapCoord(37.501, 127.01)), 900.0, 600, emptyList()),
        )

    private fun tmapReturning(): TmapRouteClient {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetchRoute(any(), any(), any(), any(), eq(emptyList()))).thenReturn(tmapRoutes.first())
        whenever(tmap.fetchRoute(any(), any(), any(), any(), argThat { isNotEmpty() })).thenReturn(tmapRoutes.first())
        return tmap
    }

    private fun serviceOf(tmap: TmapRouteClient): SafeRouteService =
        SafeRouteService(
            routeFinder = RouteFinder(tmap, SafeWaypointFinder(source)),
            candidateMerger = RouteCandidateMerger(),
            typeAssigner = RouteTypeAssigner(nightScorer(source)),
            assembler = RouteOptionAssembler(RouteMarkerBuilder(source), source, RouteTypeCatalog()),
            routePlannedHandler = mock(),
        )

    @Test
    fun `재탐색은 우회 후보를 부르지 않는다`() {
        val tmap = tmapReturning()

        serviceOf(tmap).safeCompare(queryOf(reroute = true))

        verify(tmap, never()).fetchRoute(any(), any(), any(), any(), argThat { isNotEmpty() })
    }

    @Test
    fun `처음 찾는 요청은 우회 후보를 그대로 부른다`() {
        val tmap = tmapReturning()

        serviceOf(tmap).safeCompare(queryOf(reroute = false))

        verify(tmap, org.mockito.kotlin.atLeastOnce()).fetchRoute(any(), any(), any(), any(), argThat { isNotEmpty() })
    }

    @Test
    fun `재탐색도 걸을 수 있는 경로를 돌려준다`() {
        val result = serviceOf(tmapReturning()).safeCompare(queryOf(reroute = true))

        assertThat(result).isNotEmpty()
        assertThat(result.first().path).isNotNull()
    }
}
