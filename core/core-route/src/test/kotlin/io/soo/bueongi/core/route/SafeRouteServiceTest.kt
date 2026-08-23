package io.soo.bueongi.core.route

import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.core.route.domain.service.RouteCandidateCollector
import io.soo.bueongi.core.route.domain.service.RouteCandidateMerger
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.service.RouteOptionAssembler
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.core.route.domain.service.RouteService
import io.soo.bueongi.core.route.domain.service.SafeRouteService
import io.soo.bueongi.core.enums.SafetySpotPoiType
import io.soo.bueongi.core.route.domain.model.RouteTag
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.route.domain.model.RouteTagVariant

import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient

import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.core.route.domain.safety.service.SafeDetourPlanner
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SafeRouteServiceTest {

    private val origin = GeoPoint(37.5, 127.0)
    private val dest = GeoPoint(37.5, 127.01)

    private val request = RouteQuery(
        origin = origin,
        destination = dest,
        destinationName = "목적지",
        routeType = RouteType.SAFE,
        safetyPreference = SafetyPreference.SAFE,
    )

    private val cctv = listOf(127.002, 127.004, 127.006, 127.008).map {
        SafetySpotPoint(SafetySpotType.CCTV, 37.5, it)
    }
    private val source = FakeSafetySpotSource(mapOf(SafetySpotType.CCTV to cctv))
    private val scorer = nightScorer(source)
    private val detourPlanner = SafeDetourPlanner(source)
    private val routeService = RouteService(RouteTypeCatalog())

    private val routeA = TmapRoute(
        searchOption = "0",
        path = listOf(TmapCoord(37.5, 127.0), TmapCoord(37.5, 127.01)),
        distanceM = 880.0,
        timeS = 700,
        steps = emptyList(),
    )
    private val routeB = TmapRoute(
        searchOption = "10",
        path = listOf(TmapCoord(37.51, 127.0), TmapCoord(37.51, 127.01)),
        distanceM = 900.0,
        timeS = 600,
        steps = emptyList(),
    )

    private fun service(tmap: TmapRouteClient) = safeRouteService(tmap, scorer, detourPlanner, source)

    private fun safeRouteService(
        tmap: TmapRouteClient,
        scorer: SafetyScorer,
        detourPlanner: SafeDetourPlanner,
        source: FakeSafetySpotSource,
    ) = SafeRouteService(
        routeService = routeService,
        collector = RouteCandidateCollector(tmap, detourPlanner, RouteCandidateMerger()),
        scorer = scorer,
        assembler = RouteOptionAssembler(RouteMarkerBuilder(source), source, RouteTypeCatalog()),
    )

    @Test
    fun `Tmap 키 미설정이면 compare 폴백(3개, 점수 없음)`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(false)
        val result = service(tmap).safeCompare(request)
        assertThat(result).hasSize(3)
        assertThat(result.map { it.type }).containsExactly(RouteType.SAFE, RouteType.MAIN, RouteType.FAST)
        assertThat(result.first().score).isNull()
        assertThat(result.first().path).isNull()
        assertThat(result.first().provenance).isNull()
        assertThat(result.first().breakdown).isNull()
    }

    @Test
    fun `Tmap 전부 실패(빈 후보)면 compare 폴백`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(emptyList())
        val result = service(tmap).safeCompare(request)
        assertThat(result).hasSize(3)
        assertThat(result.first().score).isNull()
    }

    @Test
    fun `Tmap 후보를 채점해 안전점수 내림차순과 유형을 배정하고 마커를 포함`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(listOf(routeA, routeB))
        whenever(tmap.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(null)

        val result = service(tmap).safeCompare(request)

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
        val breakdown = result[0].breakdown!!
        assertThat(breakdown.cctvDensity).isGreaterThan(0.0)
        assertThat(breakdown.routeKm).isGreaterThan(0.0)
        val provenance = result[0].provenance!!
        assertThat(provenance.kind).isEqualTo("live")
        assertThat(provenance.origin).isNotBlank()
    }

    @Test
    fun `회랑 실측이 없는 유형은 마커를 지어내지 않는다(정직한 0)`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(listOf(routeA, routeB))
        whenever(tmap.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(null)

        val safe = service(tmap).safeCompare(request).first { it.type == RouteType.SAFE }
        val markerTypes = safe.markers!!.map { it.type }
        assertThat(markerTypes).contains(SafetySpotPoiType.START, SafetySpotPoiType.END, SafetySpotPoiType.CCTV)
        assertThat(markerTypes).doesNotContain(SafetySpotPoiType.STORE, SafetySpotPoiType.POLICE, SafetySpotPoiType.SAFEHOUSE)
    }

    @Test
    fun `회랑에 실 지구대가 있으면 추정으로 중복 채우지 않는다`() {
        val police = listOf(SafetySpotPoint(SafetySpotType.POLICE, 37.5, 127.005, name = "역삼지구대"))
        val richSource = FakeSafetySpotSource(mapOf(SafetySpotType.CCTV to cctv, SafetySpotType.POLICE to police))
        val svc = safeRouteService(
            tmap = mock<TmapRouteClient>().also {
                whenever(it.isEnabled).thenReturn(true)
                whenever(it.fetchRoutes(any(), any(), anyOrNull())).thenReturn(listOf(routeA, routeB))
                whenever(it.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(null)
            },
            scorer = nightScorer(richSource),
            detourPlanner = SafeDetourPlanner(richSource),
            source = richSource,
        )

        val safe = svc.safeCompare(request).first { it.type == RouteType.SAFE }
        val policeMarkers = safe.markers!!.filter { it.type == SafetySpotPoiType.POLICE }
        assertThat(policeMarkers).hasSize(1)
        assertThat(policeMarkers.first().name).isEqualTo("역삼지구대")
    }

    @Test
    fun `안심 우회 후보는 거리 예산을 넘으면 제외된다`() {
        // 기준 880m · SAFE 선호(예산 +35%) → 상한 1188m. 병합에 먹히지 않도록 경로를 멀리 둔다.
        val overBudget = detourAt(lat = 37.53, distanceM = 2000.0)

        val result = service(tmapReturning(detour = overBudget)).safeCompare(request)

        assertThat(result).hasSize(1)
        assertThat(result.map { it.dist }).doesNotContain("2.0km")
    }

    @Test
    fun `거리 예산 안에 드는 우회 후보는 채택된다`() {
        val withinBudget = detourAt(lat = 37.53, distanceM = 1000.0)

        val result = service(tmapReturning(detour = withinBudget)).safeCompare(request)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.dist }).contains("1.0km")
    }

    private fun detourAt(lat: Double, distanceM: Double): TmapRoute =
        TmapRoute(
            searchOption = "2",
            path = listOf(TmapCoord(lat, 127.0), TmapCoord(lat, 127.01)),
            distanceM = distanceM,
            timeS = 1500,
            steps = emptyList(),
        )

    private fun tmapReturning(detour: TmapRoute): TmapRouteClient = mock<TmapRouteClient>().also {
        whenever(it.isEnabled).thenReturn(true)
        whenever(it.fetchRoutes(any(), any(), anyOrNull())).thenReturn(listOf(routeA))
        whenever(it.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(detour)
    }

    @Test
    fun `거의 겹치는 후보는 병합되어 유형당 하나씩 정확히 3개로 정규화된다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        val rA = TmapRoute("0", listOf(TmapCoord(37.5, 127.0), TmapCoord(37.5, 127.005), TmapCoord(37.5, 127.01)), 880.0, 700, emptyList())
        val rADup = TmapRoute("4", listOf(TmapCoord(37.5, 127.0), TmapCoord(37.5, 127.01)), 884.0, 690, emptyList())
        val rB = TmapRoute("10", listOf(TmapCoord(37.51, 127.0), TmapCoord(37.51, 127.01)), 900.0, 600, emptyList())
        val rC = TmapRoute("0", listOf(TmapCoord(37.52, 127.0), TmapCoord(37.52, 127.01)), 950.0, 800, emptyList())
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(listOf(rA, rADup, rB, rC))
        whenever(tmap.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(null)

        val result = service(tmap).safeCompare(request)

        assertThat(result).hasSize(3)
        assertThat(result.map { it.type }).containsExactlyInAnyOrder(RouteType.SAFE, RouteType.MAIN, RouteType.FAST)
        assertThat(result.map { it.type }).doesNotHaveDuplicates()
    }

    @Test
    fun `Tmap 미설정 폴백은 compare 를 record 하지 않고 safe-compare 만 1회 집계한다`() {
        val recording = RecordingRouteStatsStore()
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(false)
        val svc = SafeRouteService(
            routeService = RouteService(RouteTypeCatalog(), recording),
            collector = RouteCandidateCollector(tmap, detourPlanner, RouteCandidateMerger()),
            scorer = scorer,
            assembler = RouteOptionAssembler(RouteMarkerBuilder(source), source, RouteTypeCatalog()),
            routeStatsStore = recording,
        )

        svc.safeCompare(request)

        assertThat(recording.records.map { it.endpoint }).containsExactly("safe-compare")
        assertThat(recording.records.first().preference).isEqualTo(SafetyPreference.SAFE)
    }

    @Test
    fun `Tmap 전부 실패 폴백도 safe-compare 만 1회 집계한다`() {
        val recording = RecordingRouteStatsStore()
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(emptyList())
        val svc = SafeRouteService(
            routeService = RouteService(RouteTypeCatalog(), recording),
            collector = RouteCandidateCollector(tmap, detourPlanner, RouteCandidateMerger()),
            scorer = scorer,
            assembler = RouteOptionAssembler(RouteMarkerBuilder(source), source, RouteTypeCatalog()),
            routeStatsStore = recording,
        )

        svc.safeCompare(request)

        assertThat(recording.records.map { it.endpoint }).containsExactly("safe-compare")
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
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(listOf(r0, r1, r2, r3))
        whenever(tmap.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(null)

        val result = service(tmap).safeCompare(request)

        assertThat(result).hasSize(3)
        assertThat(result.map { it.type }).containsExactlyInAnyOrder(RouteType.SAFE, RouteType.MAIN, RouteType.FAST)
    }
}

class RecordingRouteStatsStore : RouteStatsStore {
    val records: MutableList<RouteRecommendationLog> = mutableListOf<RouteRecommendationLog>()

    override fun record(log: RouteRecommendationLog) {
        records.add(log)
    }

    override fun findRecent(limit: Int): List<RouteRecommendationLog> = records.take(limit)
}
