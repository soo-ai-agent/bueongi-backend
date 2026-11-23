package io.soo.bueongi.core.route

import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.common.request.LatLng
import io.soo.bueongi.core.common.request.NamedLatLng
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.error.RouteSearchUnavailableException
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoiType
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.model.RouteTag
import io.soo.bueongi.core.route.domain.model.RouteTagVariant
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.core.route.domain.safety.service.SafetySpotWaypointPlanner
import io.soo.bueongi.core.route.domain.service.RouteCandidateMerger
import io.soo.bueongi.core.route.domain.service.RouteFinder
import io.soo.bueongi.core.route.domain.service.RouteOptionAssembler
import io.soo.bueongi.core.route.domain.service.RouteOptionService
import io.soo.bueongi.core.route.domain.service.RouteTypeAssigner
import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RouteOptionServiceTest {
    private val request =
        RouteRequest(
            rawOrigin = LatLng(37.5, 127.0),
            rawDestination = NamedLatLng(37.5, 127.01),
            rawRouteType = "safe",
            rawSafetyPreference = "safe",
        )

    private val cctv =
        listOf(127.002, 127.004, 127.006, 127.008).map {
            SafetySpotPoint.of(SafetySpotType.CCTV, 37.5, it)
        }
    private val source = FakeSafetySpotSource(mapOf(SafetySpotType.CCTV to cctv))
    private val scorer = nightScorer(source)
    private val waypointPlanner = SafetySpotWaypointPlanner(source)

    private val routeA =
        TmapRoute(
            searchOption = "0",
            path = listOf(TmapCoord(37.5, 127.0), TmapCoord(37.5, 127.01)),
            distanceMeters = 880.0,
            timeSeconds = 700,
            steps = emptyList(),
        )
    private val routeB =
        TmapRoute(
            searchOption = "10",
            path = listOf(TmapCoord(37.51, 127.0), TmapCoord(37.51, 127.01)),
            distanceMeters = 900.0,
            timeSeconds = 600,
            steps = emptyList(),
        )

    private fun service(tmap: TmapRouteClient) = routeOptionService(tmap, scorer, waypointPlanner, source)

    private fun routeOptionService(
        tmap: TmapRouteClient,
        scorer: SafetyScorer,
        waypointPlanner: SafetySpotWaypointPlanner,
        source: FakeSafetySpotSource,
    ) = RouteOptionService(
        routeFinder = RouteFinder(tmap),
        waypointPlanner = waypointPlanner,
        candidateMerger = RouteCandidateMerger(),
        typeAssigner = RouteTypeAssigner(scorer),
        assembler = RouteOptionAssembler(RouteMarkerBuilder(source), source, RouteTypeCatalog()),
        routePlannedHandler = mock(),
    )

    @Test
    fun `Tmap 키가 없으면 경로를 지어내지 않고 사용 불가를 알린다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(false)

        assertThatThrownBy { service(tmap).find(request) }
            .isInstanceOf(RouteSearchUnavailableException::class.java)
    }

    @Test
    fun `Tmap 후보가 전멸하면 사용 불가를 알린다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetch(any(), any(), any(), any(), eq(emptyList()))).thenReturn(null)

        assertThatThrownBy { service(tmap).find(request) }
            .isInstanceOf(RouteSearchUnavailableException::class.java)
    }

    @Test
    fun `Tmap 후보를 채점해 안전점수 내림차순과 유형을 배정하고 마커를 포함`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetch(any(), any(), any(), any(), eq(emptyList()))).thenReturn(routeA, routeB)
        whenever(tmap.fetch(any(), any(), any(), any(), argThat { isNotEmpty() })).thenReturn(null)

        val result = service(tmap).find(request)

        assertThat(result).hasSize(2)
        assertThat(result[0].score!!).isGreaterThanOrEqualTo(result[1].score!!)
        assertThat(result[0].type).isEqualTo(RouteType.SAFE)
        assertThat(result[0].tags.first()).isEqualTo(RouteTag("안심", RouteTagVariant.MINT))
        assertThat(result[0].name).isEqualTo("안심 경로")
        assertThat(result[0].score!!).isGreaterThan(0)
        assertThat(result.map { it.type }).contains(RouteType.FAST)
        assertThat(result[0].path).isNotNull
        assertThat(result[0].markers).isNotNull
        val markerTypes = result[0].markers!!.map { it.type }
        assertThat(markerTypes).contains(SafetySpotPoiType.START, SafetySpotPoiType.END, SafetySpotPoiType.CCTV)
        val scoreDetail = result[0].scoreDetail!!
        assertThat(scoreDetail.cctvDensity).isGreaterThan(0.0)
        assertThat(scoreDetail.routeKm).isGreaterThan(0.0)
        val dataSource = result[0].source!!
        assertThat(dataSource.kind).isEqualTo("live")
        assertThat(dataSource.origin).isNotBlank()
    }

    @Test
    fun `회랑 실측이 없는 유형은 마커를 지어내지 않는다(정직한 0)`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetch(any(), any(), any(), any(), eq(emptyList()))).thenReturn(routeA, routeB)
        whenever(tmap.fetch(any(), any(), any(), any(), argThat { isNotEmpty() })).thenReturn(null)

        val safe = service(tmap).find(request).first { it.type == RouteType.SAFE }
        val markerTypes = safe.markers!!.map { it.type }
        assertThat(markerTypes).contains(SafetySpotPoiType.START, SafetySpotPoiType.END, SafetySpotPoiType.CCTV)
        assertThat(markerTypes).doesNotContain(SafetySpotPoiType.POLICE, SafetySpotPoiType.SAFEHOUSE)
    }

    @Test
    fun `회랑에 실 지구대가 있으면 추정으로 중복 채우지 않는다`() {
        val police = listOf(SafetySpotPoint.of(SafetySpotType.POLICE, 37.5, 127.005, name = "역삼지구대"))
        val richSource = FakeSafetySpotSource(mapOf(SafetySpotType.CCTV to cctv, SafetySpotType.POLICE to police))
        val svc =
            routeOptionService(
                tmap =
                    mock<TmapRouteClient>().also {
                        whenever(it.isEnabled).thenReturn(true)
                        whenever(it.fetch(any(), any(), any(), any(), eq(emptyList()))).thenReturn(routeA, routeB)
                        whenever(it.fetch(any(), any(), any(), any(), argThat { isNotEmpty() })).thenReturn(null)
                    },
                scorer = nightScorer(richSource),
                waypointPlanner = SafetySpotWaypointPlanner(richSource),
                source = richSource,
            )

        val safe = svc.find(request).first { it.type == RouteType.SAFE }
        val policeMarkers = safe.markers!!.filter { it.type == SafetySpotPoiType.POLICE }
        assertThat(policeMarkers).hasSize(1)
        assertThat(policeMarkers.first().name).isEqualTo("역삼지구대")
    }

    @Test
    fun `안심 우회 후보는 거리 예산을 넘으면 제외된다`() {
        val overBudget = saferRouteAt(lat = 37.53, distanceMeters = 2000.0)

        val result = service(tmapReturning(saferRoute = overBudget)).find(request)

        assertThat(result).hasSize(1)
        assertThat(result.map { it.dist }).doesNotContain("2.0km")
    }

    @Test
    fun `거리 예산 안에 드는 우회 후보는 채택된다`() {
        val withinBudget = saferRouteAt(lat = 37.53, distanceMeters = 1000.0)

        val result = service(tmapReturning(saferRoute = withinBudget)).find(request)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.dist }).contains("1.0km")
    }

    private fun saferRouteAt(lat: Double, distanceMeters: Double): TmapRoute =
        TmapRoute(
            searchOption = "2",
            path = listOf(TmapCoord(lat, 127.0), TmapCoord(lat, 127.01)),
            distanceMeters = distanceMeters,
            timeSeconds = 1500,
            steps = emptyList(),
        )

    private fun tmapReturning(saferRoute: TmapRoute): TmapRouteClient =
        mock<TmapRouteClient>().also {
            whenever(it.isEnabled).thenReturn(true)
            whenever(it.fetch(any(), any(), any(), any(), eq(emptyList()))).thenReturn(routeA)
            whenever(it.fetch(any(), any(), any(), any(), argThat { isNotEmpty() })).thenReturn(saferRoute)
        }

    @Test
    fun `거의 겹치는 후보는 병합되고 우회 후보는 기본 경로로 뽑지 않아 안심과 빠름 2개만 남는다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        val rA = TmapRoute("0", listOf(TmapCoord(37.5, 127.0), TmapCoord(37.5, 127.005), TmapCoord(37.5, 127.01)), 880.0, 700, emptyList())
        val rADup = TmapRoute("4", listOf(TmapCoord(37.5, 127.0), TmapCoord(37.5, 127.01)), 884.0, 690, emptyList())
        val rB = TmapRoute("10", listOf(TmapCoord(37.51, 127.0), TmapCoord(37.51, 127.01)), 900.0, 600, emptyList())
        val rC = TmapRoute("0", listOf(TmapCoord(37.52, 127.0), TmapCoord(37.52, 127.01)), 950.0, 800, emptyList())
        whenever(tmap.fetch(any(), any(), any(), any(), eq(emptyList()))).thenReturn(rA, rADup, rB)
        whenever(tmap.fetch(any(), any(), any(), any(), argThat { isNotEmpty() })).thenReturn(rC)

        val result = service(tmap).find(request)

        // rADup 이 병합되지 않으면 기본 자리를 채워 3개가 된다. 남은 rC 는 우회 후보라 기본 자리에 못 간다.
        assertThat(result).hasSize(2)
        assertThat(result.map { it.type }).containsExactlyInAnyOrder(RouteType.SAFE, RouteType.FAST)
        assertThat(result.map { it.dist }).containsExactlyInAnyOrder("880m", "900m")
    }

    @Test
    fun `서로 다른 후보가 4개여도 유형당 하나씩 정확히 3개만 반환한다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)

        fun line(lat: Double) = listOf(TmapCoord(lat, 127.0), TmapCoord(lat, 127.01))
        val r0 = TmapRoute("0", line(37.5), 880.0, 700, emptyList())
        val r1 = TmapRoute("4", line(37.51), 900.0, 600, emptyList())
        val r2 = TmapRoute("10", line(37.52), 920.0, 750, emptyList())
        val r3 = TmapRoute("0", line(37.53), 940.0, 800, emptyList())
        whenever(tmap.fetch(any(), any(), any(), any(), eq(emptyList()))).thenReturn(r0, r1, r2)
        whenever(tmap.fetch(any(), any(), any(), any(), argThat { isNotEmpty() })).thenReturn(r3)

        val result = service(tmap).find(request)

        assertThat(result).hasSize(3)
        assertThat(result.map { it.type }).containsExactlyInAnyOrder(RouteType.SAFE, RouteType.MAIN, RouteType.FAST)
    }
}
