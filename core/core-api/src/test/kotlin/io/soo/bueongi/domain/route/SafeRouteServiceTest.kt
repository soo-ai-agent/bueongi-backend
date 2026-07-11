package io.soo.bueongi.domain.route

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient

import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.domain.facility.FakeFacilitySource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.domain.facility.SafeDetourPlanner
import io.soo.bueongi.domain.facility.SafetyScorer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [SafeRouteService] 단위 테스트. Tmap 은 목으로 격리하고 실 점수/우회/마커 파이프라인을 검증한다:
 *  - 키 미설정/Tmap 전부 실패 → compare 폴백(점수·마커 없음)
 *  - 키 설정 → 후보 채점·유형배정(safe=최고점/fast=최소시간)·점수 내림차순·경로변 마커 포함
 *  - 안심 우회 후보는 기본 최단거리 ×(1+budget) 이내만 채택
 */
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

    // routeA 회랑 위 CCTV 4개 → A 점수 > 0. routeB(북쪽 ~1.1km offset)는 시설 없음 → 점수 0.
    private val cctv = listOf(127.002, 127.004, 127.006, 127.008).map {
        FacilityPoint(FacilityType.CCTV, 37.5, it)
    }
    private val source = FakeFacilitySource(mapOf(FacilityType.CCTV to cctv))
    private val scorer = SafetyScorer(source)
    private val detourPlanner = SafeDetourPlanner(source)
    private val routeService = RouteService()

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
        timeS = 600, // A 보다 빠름 → fast 배정
        steps = emptyList(),
    )

    private fun service(tmap: TmapRouteClient) =
        SafeRouteService(
            routeService,
            tmap,
            scorer,
            detourPlanner,
            source,
            RouteMarkerBuilder(source),
            RouteCandidateMerger(),
        )

    @Test
    fun `Tmap 키 미설정이면 compare 폴백(3개, 점수 없음)`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(false)
        val result = service(tmap).safeCompare(request)
        assertThat(result).hasSize(3)
        assertThat(result.map { it.type }).containsExactly("safe", "main", "fast")
        assertThat(result.first().score).isNull()
        assertThat(result.first().path).isNull()
        // 폴백엔 출처(provenance)·근거(breakdown)가 없다 → 프론트가 '예시 데이터'로 표기(P0-1).
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
        // 우회 호출은 후보를 늘리지 않게 null(없음)로 둔다 — 기본 후보 채점만 검증.
        whenever(tmap.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(null)

        val result = service(tmap).safeCompare(request)

        assertThat(result).hasSize(2)
        // 점수 내림차순: A(시설 있음)가 먼저.
        assertThat(result[0].score!!).isGreaterThanOrEqualTo(result[1].score!!)
        assertThat(result[0].type).isEqualTo("safe")
        assertThat(result[0].score!!).isGreaterThan(0)
        assertThat(result.map { it.type }).contains("fast")
        // safe 경로 path/steps/markers 채움.
        assertThat(result[0].path).isNotNull
        assertThat(result[0].markers).isNotNull
        // 마커는 출발/도착 + 회랑 내 CCTV 를 포함.
        val markerTypes = result[0].markers!!.map { it.type }
        assertThat(markerTypes).contains("start", "end", "cctv")
        // P0-4 점수 근거: 회랑 내 CCTV 4개/0.888km ≈ 4.5/km 가 그대로 노출된다.
        val breakdown = result[0].breakdown!!
        assertThat(breakdown.cctvDensity).isGreaterThan(0.0)
        assertThat(breakdown.routeKm).isGreaterThan(0.0)
        // P0-1·P0-3 출처: 실데이터 산출엔 provenance(kind=live, origin)가 붙는다.
        val provenance = result[0].provenance!!
        assertThat(provenance.kind).isEqualTo("live")
        assertThat(provenance.origin).isNotBlank()
    }

    @Test
    fun `마커는 회랑 실측이 없어도 그리드 유형(store police safehouse)을 경로 위에 채운다`() {
        // 회귀 방어: 편의점(store)은 실 공간데이터가 없고, 지구대·안심집은 좁은 30m 회랑을 벗어나면 실측 0이라
        // 프론트 시설 그리드가 store/police/safehouse=0 으로 떨어졌다(마커만 소비하도록 전환된 뒤 드러난 결함).
        // 이제 실측 0인 그리드 유형은 /facilities 와 같은 개수 근거로 실제 경로 위에 표시되어야 한다.
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(listOf(routeA, routeB))
        whenever(tmap.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(null)

        val safe = service(tmap).safeCompare(request).first { it.type == "safe" }
        val markerTypes = safe.markers!!.map { it.type }
        // 실측(cctv) + 추정 보완(store/police/safehouse)이 함께 마커에 담긴다.
        assertThat(markerTypes).contains("cctv", "store", "police", "safehouse")
        // 채워진 시설 마커의 좌표는 실제 경로(출발↔도착) 경계 안에 있다(회랑 밖 이질감 방지).
        val filled = safe.markers!!.filter { it.type in listOf("store", "police", "safehouse") }
        assertThat(filled).isNotEmpty()
        for (m in filled) {
            assertThat(m.lat).isBetween(minOf(origin.lat, dest.lat), maxOf(origin.lat, dest.lat))
            assertThat(m.lng).isBetween(minOf(origin.lng, dest.lng), maxOf(origin.lng, dest.lng))
            assertThat(m.x).isBetween(0, 100)
            assertThat(m.y).isBetween(0, 100)
        }
    }

    @Test
    fun `회랑에 실 지구대가 있으면 추정으로 중복 채우지 않는다`() {
        // 실 데이터 우선: 회랑 안에 실 지구대가 있으면 그 실좌표(메타 포함)만 쓰고 추정 보완은 하지 않는다.
        val police = listOf(FacilityPoint(FacilityType.POLICE, 37.5, 127.005, name = "역삼지구대"))
        val richSource = FakeFacilitySource(mapOf(FacilityType.CCTV to cctv, FacilityType.POLICE to police))
        val svc = SafeRouteService(
            routeService,
            mock<TmapRouteClient>().also {
                whenever(it.isEnabled).thenReturn(true)
                whenever(it.fetchRoutes(any(), any(), anyOrNull())).thenReturn(listOf(routeA, routeB))
                whenever(it.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(null)
            },
            SafetyScorer(richSource),
            SafeDetourPlanner(richSource),
            richSource,
            RouteMarkerBuilder(richSource),
            RouteCandidateMerger(),
        )

        val safe = svc.safeCompare(request).first { it.type == "safe" }
        val policeMarkers = safe.markers!!.filter { it.type == "police" }
        // 실 지구대 1개만(추정 보완 없음) — 이름(메타)이 보존된다.
        assertThat(policeMarkers).hasSize(1)
        assertThat(policeMarkers.first().name).isEqualTo("역삼지구대")
    }

    @Test
    fun `안심 우회 후보는 거리 예산을 넘으면 제외된다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(listOf(routeA))
        // 기본 최단 880m, safe budget 0.35 → 1188m 초과 우회는 제외. 2000m 우회 반환.
        val longDetour = routeA.copy(searchOption = "0", distanceM = 2000.0, timeS = 1500)
        whenever(tmap.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(longDetour)

        val result = service(tmap).safeCompare(request)
        // 과도 우회는 채택되지 않아 기본 후보 1개만 남는다.
        assertThat(result).hasSize(1)
        assertThat(result.first().dist).isNotEqualTo("2.0km")
    }

    @Test
    fun `거의 겹치는 후보는 병합되어 유형당 하나씩 정확히 3개로 정규화된다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        // rA(회랑에 CCTV) 와 rADup 은 같은 도로(37.5)를 90%+ 공유 → 병합 대상. rB/rC 는 서로 다른 길.
        val rA = TmapRoute("0", listOf(TmapCoord(37.5, 127.0), TmapCoord(37.5, 127.005), TmapCoord(37.5, 127.01)), 880.0, 700, emptyList())
        val rADup = TmapRoute("4", listOf(TmapCoord(37.5, 127.0), TmapCoord(37.5, 127.01)), 884.0, 690, emptyList())
        val rB = TmapRoute("10", listOf(TmapCoord(37.51, 127.0), TmapCoord(37.51, 127.01)), 900.0, 600, emptyList())
        val rC = TmapRoute("0", listOf(TmapCoord(37.52, 127.0), TmapCoord(37.52, 127.01)), 950.0, 800, emptyList())
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(listOf(rA, rADup, rB, rC))
        whenever(tmap.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(null)

        val result = service(tmap).safeCompare(request)

        // rADup 은 rA 와 병합돼 사라지고, 남은 3개 실경로에 유형이 하나씩 배정된다(중복 라벨 없음).
        assertThat(result).hasSize(3)
        assertThat(result.map { it.type }).containsExactlyInAnyOrder("safe", "main", "fast")
        assertThat(result.map { it.type }).doesNotHaveDuplicates()
    }

    @Test
    fun `서로 다른 후보가 4개여도 유형당 하나씩 정확히 3개만 반환한다`() {
        val tmap = mock<TmapRouteClient>()
        whenever(tmap.isEnabled).thenReturn(true)
        // 겹치지 않는 4개 후보(위도 상이) → 정규화로 safe/main/fast 3개만.
        fun line(lat: Double) = listOf(TmapCoord(lat, 127.0), TmapCoord(lat, 127.01))
        val r0 = TmapRoute("0", line(37.5), 880.0, 700, emptyList()) // 회랑에 CCTV → safe
        val r1 = TmapRoute("4", line(37.51), 900.0, 600, emptyList()) // 최소시간 → fast
        val r2 = TmapRoute("10", line(37.52), 920.0, 750, emptyList())
        val r3 = TmapRoute("0", line(37.53), 940.0, 800, emptyList())
        whenever(tmap.fetchRoutes(any(), any(), anyOrNull())).thenReturn(listOf(r0, r1, r2, r3))
        whenever(tmap.fetchRouteVia(any(), any(), any(), anyOrNull())).thenReturn(null)

        val result = service(tmap).safeCompare(request)

        assertThat(result).hasSize(3)
        assertThat(result.map { it.type }).containsExactlyInAnyOrder("safe", "main", "fast")
    }
}
