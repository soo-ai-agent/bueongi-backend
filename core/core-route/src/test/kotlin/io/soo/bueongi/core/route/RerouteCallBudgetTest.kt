package io.soo.bueongi.core.route

import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.core.route.domain.safety.service.SafeDetourPlanner
import io.soo.bueongi.core.route.domain.service.RouteCandidateCollector
import io.soo.bueongi.core.route.domain.service.RouteCandidateMerger
import io.soo.bueongi.core.route.domain.service.RouteOptionAssembler
import io.soo.bueongi.core.route.domain.service.RouteService
import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.core.route.domain.service.SafeRouteService
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RerouteCallBudgetTest {
    private val origin = GeoPoint(37.5, 127.0)
    private val dest = GeoPoint(37.5, 127.01)

    private fun queryOf(reroute: Boolean): RouteQuery =
        RouteQuery(
            origin = origin,
            destination = dest,
            destinationName = "목적지",
            routeType = RouteType.SAFE,
            safetyPreference = SafetyPreference.SAFE,
            reroute = reroute,
        )

    private val source =
        FakeSafetySpotSource(
            mapOf(
                SafetySpotType.CCTV to
                    listOf(127.002, 127.004, 127.006, 127.008).map {
                        SafetySpotPoint(SafetySpotType.CCTV, 37.5, it)
                    },
            ),
        )

    private val base =
        listOf(
            TmapRoute("0", listOf(TmapCoord(37.5, 127.0), TmapCoord(37.5, 127.01)), 880.0, 700, emptyList()),
            TmapRoute("10", listOf(TmapCoord(37.501, 127.0), TmapCoord(37.501, 127.01)), 900.0, 600, emptyList()),
        )

    private fun tmapReturning(): TmapRouteClient {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(base)
        whenever(tmap.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(base.first())
        return tmap
    }

    private fun serviceOf(tmap: TmapRouteClient): SafeRouteService =
        SafeRouteService(
            routeService = RouteService(RouteTypeCatalog()),
            collector = RouteCandidateCollector(tmap, SafeDetourPlanner(source), RouteCandidateMerger()),
            scorer = nightScorer(source),
            assembler = RouteOptionAssembler(RouteMarkerBuilder(source), source, RouteTypeCatalog()),
        )

    @Test
    fun `재탐색은 우회 후보를 부르지 않는다`() {
        val tmap = tmapReturning()

        serviceOf(tmap).safeCompare(queryOf(reroute = true))

        verify(tmap, never()).fetchRouteVia(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `처음 찾는 요청은 우회 후보를 그대로 부른다`() {
        val tmap = tmapReturning()

        serviceOf(tmap).safeCompare(queryOf(reroute = false))

        verify(tmap, org.mockito.kotlin.atLeastOnce()).fetchRouteVia(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `재탐색도 걸을 수 있는 경로를 돌려준다`() {
        val result = serviceOf(tmapReturning()).safeCompare(queryOf(reroute = true))

        assertThat(result).isNotEmpty()
        assertThat(result.first().path).isNotNull()
    }

    @Test
    fun `재탐색은 추천 통계에 세지 않는다`() {
        val recorded: MutableList<RouteRecommendationLog> = mutableListOf()
        val store =
            object : RouteStatsStore {
                override fun record(log: RouteRecommendationLog) {
                    recorded.add(log)
                }

                override fun findRecent(limit: Int): List<RouteRecommendationLog> = recorded
            }
        val service =
            SafeRouteService(
                routeService = RouteService(RouteTypeCatalog()),
                collector = RouteCandidateCollector(tmapReturning(), SafeDetourPlanner(source), RouteCandidateMerger()),
                scorer = nightScorer(source),
                assembler = RouteOptionAssembler(RouteMarkerBuilder(source), source, RouteTypeCatalog()),
                routeStatsStore = store,
            )

        service.safeCompare(queryOf(reroute = false))
        service.safeCompare(queryOf(reroute = true))
        service.safeCompare(queryOf(reroute = true))

        assertThat(recorded).hasSize(1)
    }
}
