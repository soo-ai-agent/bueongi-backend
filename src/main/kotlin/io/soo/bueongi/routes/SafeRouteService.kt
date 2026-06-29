package io.soo.bueongi.routes

import io.soo.bueongi.facility.FacilitySpatialSource
import io.soo.bueongi.facility.FacilityType
import io.soo.bueongi.facility.GeoPoint
import io.soo.bueongi.facility.SafetyScore
import io.soo.bueongi.facility.SafetyScorer
import io.soo.bueongi.facility.SafeDetourPlanner
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 안심 라우팅 서비스(POST /api/routes/safe-compare).
 *
 * Tmap 키가 있으면: 기본 후보(추천/대로/최단) + 안심 경유지 우회 후보를 모아 실 공공데이터(회랑 쿼리)로 채점하고,
 * 우회 후보는 "기본 최단거리 ×(1+detourBudget)" 이내만 채택한다. 안전점수 내림차순으로 유형(safe=최고점,
 * fast=최소시간, main=나머지)을 배정하고 경로변 시설 마커를 포함해 반환한다.
 * 키가 없거나 Tmap 전부 실패면 기존 [RouteService.compare] 로 폴백(점수/마커 없이).
 *
 * 프론트 directRoute.ts(scoreAndType/toRouteOptions/buildRouteMarkers)의 Kotlin 이식 — 같은 입력/데이터 →
 * 같은 결과(결정론적).
 */
@Service
class SafeRouteService(
    private val routeService: RouteService,
    private val tmapClient: TmapRouteClient,
    private val scorer: SafetyScorer,
    private val detourPlanner: SafeDetourPlanner,
    private val repository: FacilitySpatialSource,
) {

    fun safeCompare(request: RouteRequest): List<RouteOption> {
        val o = request.validatedOrigin()
        val d = request.destination!!
        // Tmap 미설정이면 기존 결정론적 폴백(점수/마커 없는 RouteOption).
        if (!tmapClient.isEnabled) return routeService.compare(request)

        val origin = GeoPoint(o.lat!!, o.lng!!)
        val dest = GeoPoint(d.lat!!, d.lng!!)
        val budget = detourBudget(request.safetyPreference)

        val base = tmapClient.fetchRoutes(origin, dest, d.name)
        // Tmap 전부 실패 → 폴백(추천 끊김 방지).
        if (base.isEmpty()) return routeService.compare(request)

        val baseShortest = base.minOf { it.distanceM }

        // 안심 경유지 우회 후보: 추천 경로 주변 시설 앵커 → 경유지 조합 → Tmap 경유 호출.
        val directPath = base.first().path
        val anchors = detourPlanner.extractSafeAnchors(directPath)
        val combos = detourPlanner.pickDetourWaypoints(origin, dest, anchors)
        val detours = combos.mapNotNull { tmapClient.fetchRouteVia(origin, dest, it, destName = d.name) }
            // 과도 우회 차단: 기본 최단거리의 (1+budget) 이내만 채택.
            .filter { it.distanceM <= baseShortest * (1 + budget) }

        val candidates = dedupe(base + detours)
        val scored = scoreAndType(candidates)
        return toRouteOptions(scored, origin, dest)
    }

    /** 거리+좌표수가 같은 후보는 같은 경로로 보고 중복 제거(기본+우회 합류 시). */
    private fun dedupe(routes: List<TmapRoute>): List<TmapRoute> {
        val seen = HashSet<Long>()
        return routes.filter { seen.add((it.distanceM.roundToLong() * 1000) + it.path.size) }
    }

    /**
     * 후보에 안전 점수를 매기고 유형을 배정한다. safe=최고점, fast=최소시간, 나머지=main.
     * 동률은 먼저 나온 후보 우선. 안심과 빠른이 같으면 빠른은 다른 후보로 양보(프론트 scoreAndType 동형).
     */
    private fun scoreAndType(routes: List<TmapRoute>): List<ScoredRoute> {
        if (routes.isEmpty()) return emptyList()
        val scored = routes.map { it to scorer.score(it.path) }

        var safeIdx = 0
        var fastIdx = 0
        scored.forEachIndexed { i, (route, score) ->
            if (score.score > scored[safeIdx].second.score) safeIdx = i
            if (route.timeS < scored[fastIdx].first.timeS) fastIdx = i
        }
        if (fastIdx == safeIdx && scored.size > 1) {
            fastIdx = scored.indices.first { it != safeIdx }
        }
        return scored.mapIndexed { i, (route, score) ->
            val type = when (i) {
                safeIdx -> "safe"
                fastIdx -> "fast"
                else -> "main"
            }
            ScoredRoute(route, score, type)
        }
    }

    /** ScoredRoute → 프론트 계약 RouteOption. 안전 점수 내림차순 정렬(동률은 입력 순서 유지). */
    private fun toRouteOptions(scoredRoutes: List<ScoredRoute>, origin: GeoPoint, dest: GeoPoint): List<RouteOption> =
        scoredRoutes
            .sortedByDescending { it.score.score }
            .mapIndexed { i, sr ->
                RouteOption(
                    id = "tmap-${sr.route.searchOption}-$i",
                    name = TYPE_NAME.getValue(sr.type),
                    time = "${max(1, (sr.route.timeS / 60.0).roundToInt())}분",
                    dist = Geo.formatDistance(sr.route.distanceM),
                    desc = "안전 점수 ${sr.score.score}점 · CCTV ${sr.score.breakdown.cctvDensity}/km",
                    tags = buildTags(sr),
                    type = sr.type,
                    path = sr.route.path.map { LatLng(it.lat, it.lng) },
                    steps = sr.route.steps,
                    score = sr.score.score,
                    markers = buildMarkers(origin, dest, sr.route.path),
                )
            }

    private fun buildTags(sr: ScoredRoute): List<RouteTag> = buildList {
        if (sr.type == "safe") add(RouteTag("안심", "mint"))
        if (sr.type == "fast") add(RouteTag("빠른길", "blue"))
        val b = sr.score.breakdown
        if (b.cctvDensity >= 5) add(RouteTag("CCTV 많음", "mint"))
        if (b.lampDensity >= 10) add(RouteTag("밝은 길", "yellow"))
        if (b.safePathOverlap >= 0.2) add(RouteTag("안심귀갓길", "mint"))
        if (b.safehouseCount >= 1) add(RouteTag("안심지킴이집", "outline"))
    }

    /**
     * 경로변 거점 마커: 출발/도착 + 회랑 내 CCTV/안심집/비상벨. 실지도용 lat/lng 는 그대로 두고,
     * MapMock 폴백용 정규화 x/y(0~100, 여백 12%)는 전 마커 경계상자에 투영한다(프론트 buildRouteMarkers 동형).
     */
    private fun buildMarkers(origin: GeoPoint, dest: GeoPoint, path: List<GeoPoint>): List<FacilityPoi> {
        val corridor = SafetyScorer.DEFAULT_CORRIDOR_METERS
        val raw = ArrayList<FacilityPoi>()
        raw.add(FacilityPoi(type = "start", x = 0, y = 0, lat = origin.lat, lng = origin.lng))
        raw.add(FacilityPoi(type = "end", x = 0, y = 0, lat = dest.lat, lng = dest.lng))
        for ((markerType, facilityType) in MARKER_TYPES) {
            for (f in repository.index(facilityType).withinCorridor(path, corridor)) {
                raw.add(
                    FacilityPoi(
                        type = markerType,
                        x = 0,
                        y = 0,
                        lat = f.lat,
                        lng = f.lng,
                        name = f.name,
                        purpose = f.purpose,
                        cameraCount = f.cameraCount,
                    ),
                )
            }
        }

        val minLat = raw.minOf { it.lat }
        val maxLat = raw.maxOf { it.lat }
        val minLng = raw.minOf { it.lng }
        val maxLng = raw.maxOf { it.lng }
        val range = 100.0 - 2 * MARKER_PADDING
        val spanLat = maxLat - minLat
        val spanLng = maxLng - minLng
        return raw.map { m ->
            // 한 축이 퇴화(모든 좌표 동일)하면 가운데(50). 위도는 위쪽이 큰 값이라 y축을 뒤집는다.
            val x = if (spanLng == 0.0) 50 else (MARKER_PADDING + (m.lng - minLng) / spanLng * range).roundToInt()
            val y = if (spanLat == 0.0) 50 else (MARKER_PADDING + (maxLat - m.lat) / spanLat * range).roundToInt()
            m.copy(x = x.coerceIn(0, 100), y = y.coerceIn(0, 100))
        }
    }

    /** 안심 강도 선호 → 우회 허용 예산. 미상/누락이면 "safe"(0.35). */
    private fun detourBudget(preference: String?): Double = when (preference?.trim()?.lowercase()) {
        "balanced" -> 0.2
        "safest" -> 0.6
        else -> 0.35 // "safe" 및 기본
    }

    private data class ScoredRoute(val route: TmapRoute, val score: SafetyScore, val type: String)

    private companion object {
        val TYPE_NAME = mapOf("safe" to "안심 경로", "main" to "기본 경로", "fast" to "빠른 경로")
        // 마커 표시 종류 → 시설 인덱스(조명은 점수에만 반영, 마커 제외 — 프론트 MVP 기본 마커 3종).
        val MARKER_TYPES = listOf(
            "cctv" to FacilityType.CCTV,
            "safehouse" to FacilityType.SAFEHOUSE,
            "bell" to FacilityType.BELL,
        )
        const val MARKER_PADDING = 12.0
    }
}
