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
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.core.route.domain.service.RouteCandidateCollector
import io.soo.bueongi.core.route.domain.service.RouteCandidateMerger
import io.soo.bueongi.core.route.domain.service.RouteOptionAssembler
import io.soo.bueongi.core.route.domain.service.RouteService
import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.core.route.domain.service.SafeRouteService
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

/**
 * 안내 중 경로를 벗어나면 20초마다 재탐색이 나간다. 그런데 화면은 걷던 종류 하나만 쓰고
 * 나머지를 버리므로, 우회 후보를 만드는 Tmap 호출(최대 4회)은 통째로 낭비였다.
 * 30분 걷기 한 건이 첫 조회 수십 배의 호출을 태울 수 있었다.
 */
class RerouteCallBudgetTest {

    private val origin = GeoPoint(37.5, 127.0)
    private val dest = GeoPoint(37.5, 127.01)

    private fun queryOf(reroute: Boolean): RouteQuery = RouteQuery(
        origin = origin,
        destination = dest,
        destinationName = "목적지",
        routeType = RouteType.SAFE,
        safetyPreference = SafetyPreference.SAFE,
        reroute = reroute,
    )

    /** 경로 회랑에 시설이 깔려 있어야 우회 경유지가 뽑힌다 — 안 그러면 대조군이 성립하지 않는다. */
    private val source = FakeSafetySpotSource(
        mapOf(
            SafetySpotType.CCTV to listOf(127.002, 127.004, 127.006, 127.008).map {
                SafetySpotPoint(SafetySpotType.CCTV, 37.5, it)
            },
        ),
    )

    private val base = listOf(
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

    private fun serviceOf(tmap: TmapRouteClient): SafeRouteService = SafeRouteService(
        routeService = RouteService(RouteTypeCatalog()),
        collector = RouteCandidateCollector(tmap, SafeDetourPlanner(source), RouteCandidateMerger()),
        scorer = SafetyScorer(source),
        assembler = RouteOptionAssembler(RouteMarkerBuilder(source), source, RouteTypeCatalog()),
    )

    @Test
    fun `재탐색은 우회 후보를 부르지 않는다`() {
        val tmap = tmapReturning()

        serviceOf(tmap).safeCompare(queryOf(reroute = true))

        verify(tmap, never()).fetchRouteVia(any(), any(), any(), anyOrNull())
    }

    /** 처음 찾을 때는 대안을 보여 주므로 우회 후보가 그대로 필요하다. */
    @Test
    fun `처음 찾는 요청은 우회 후보를 그대로 부른다`() {
        val tmap = tmapReturning()

        serviceOf(tmap).safeCompare(queryOf(reroute = false))

        verify(tmap, org.mockito.kotlin.atLeastOnce()).fetchRouteVia(any(), any(), any(), anyOrNull())
    }

    /** 우회를 건너뛰어도 화면이 쓸 경로는 나와야 한다 — 안 나오면 재탐색이 실패로 떨어진다. */
    @Test
    fun `재탐색도 걸을 수 있는 경로를 돌려준다`() {
        val result = serviceOf(tmapReturning()).safeCompare(queryOf(reroute = true))

        assertThat(result).isNotEmpty()
        assertThat(result.first().path).isNotNull()
    }
}
