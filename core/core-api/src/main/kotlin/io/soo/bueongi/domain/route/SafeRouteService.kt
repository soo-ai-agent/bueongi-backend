package io.soo.bueongi.domain.route

import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.domain.facility.SafeDetourPlanner
import io.soo.bueongi.domain.facility.SafetyScore
import io.soo.bueongi.domain.facility.SafetyScorer
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 안심 라우팅 서비스(POST /api/routes/safe-compare) — 유스케이스 오케스트레이션.
 *
 * Tmap 키가 있으면: 기본 후보(추천/대로/최단) + 안심 경유지 우회 후보를 모아 실 공공데이터(회랑 쿼리)로 채점하고,
 * 우회 후보는 "기본 최단거리 ×(1+detourBudget)" 이내만 채택한다. 안전점수 내림차순으로 유형(safe=최고점,
 * fast=최소시간, main=나머지)을 배정하고 경로변 시설 마커를 포함해 반환한다.
 * 키가 없거나 Tmap 전부 실패면 기존 [RouteService.compare] 로 폴백(점수/마커 없이).
 *
 * 클라이언트 모델([TmapRoute]/[TmapCoord])은 이 서비스 경계에서 도메인 후보([RouteCandidate]/[GeoPoint])로
 * 매핑한다 — clients 모듈은 자립(스타터 규약), 공간 연산(SpatialMath)은 도메인 쪽에서만 쓴다.
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

    fun safeCompare(query: RouteQuery): List<RouteOption> {
        // Tmap 미설정이면 기존 결정론적 폴백(점수/마커 없는 RouteOption).
        if (!tmapClient.isEnabled) return routeService.compare(query)

        val origin = query.origin
        val destination = query.destination
        val budget = query.safetyPreference.detourBudget

        val base = tmapClient.fetchRoutes(origin.toTmapCoord(), destination.toTmapCoord(), query.destinationName)
        // Tmap 전부 실패 → 폴백(추천 끊김 방지).
        if (base.isEmpty()) return routeService.compare(query)

        val baseShortestM = base.minOf { it.distanceM }

        // 안심 경유지 우회 후보: 추천 경로 주변 시설 앵커 → 경유지 조합 → Tmap 경유 호출.
        val directPath = base.first().path.toGeoPoints()
        val anchors = detourPlanner.extractSafeAnchors(directPath)
        val waypointCombos = detourPlanner.pickDetourWaypoints(origin, destination, anchors)
        val detours = waypointCombos.mapNotNull { waypoints ->
            tmapClient.fetchRouteVia(
                origin.toTmapCoord(),
                destination.toTmapCoord(),
                waypoints.map { it.toTmapCoord() },
                destName = query.destinationName,
            )
        }
            // 과도 우회 차단: 기본 최단거리의 (1+budget) 이내만 채택.
            .filter { it.distanceM <= baseShortestM * (1 + budget) }

        // 1) 완전 동일(identityKey) 제거 → 2) 도메인 후보로 매핑 → 3) 거의 겹치는 경로 병합 →
        // 4) 정확히 유형당 1개(최대 3개)로 정규화.
        val candidates = mergeNearDuplicates(dedupeByIdentity(base + detours).map { it.toCandidate() })
        val scored = scoreTypeAndLimit(candidates)
        return toRouteOptions(scored, origin, destination)
    }

    /** 같은 경로([TmapRoute.identityKey] 동일) 중복 제거 — 기본+우회 합류 시. 판정 키는 클라이언트와 단일 출처. */
    private fun dedupeByIdentity(routes: List<TmapRoute>): List<TmapRoute> {
        val seen = HashSet<Long>()
        return routes.filter { seen.add(it.identityKey) }
    }

    /**
     * 거의 같은 길인데 라벨만 다른 후보를 병합한다(예: 최단과 대로우선이 같은 도로를 90%+ 공유).
     *
     * 두 경로의 겹침 길이가 (짧은 쪽 길이 × [MERGE_OVERLAP_RATIO]) 이상이면 같은 경로로 보고,
     * 입력 순서상 먼저 나온 후보(=기본 추천/대로/최단 → 우회 순)를 남긴다. 결정론적.
     * 이렇게 해야 뒤의 유형 배정(safe/main/fast)이 서로 다른 실제 경로에만 매겨진다.
     */
    private fun mergeNearDuplicates(candidates: List<RouteCandidate>): List<RouteCandidate> {
        val kept = ArrayList<RouteCandidate>()
        for (candidate in candidates) {
            if (kept.none { isNearDuplicate(it, candidate) }) kept.add(candidate)
        }
        return kept
    }

    private fun isNearDuplicate(a: RouteCandidate, b: RouteCandidate): Boolean {
        val lengthA = SpatialMath.pathLengthMeters(a.path)
        val lengthB = SpatialMath.pathLengthMeters(b.path)
        val shorter = minOf(lengthA, lengthB)
        if (shorter <= 0.0) return false
        // 양방향 겹침 중 큰 값 — 한 경로가 다른 경로에 거의 포함되면 같은 길로 본다.
        val overlap = maxOf(
            SpatialMath.overlapLengthMeters(a.path, b.path, MERGE_CORRIDOR_METERS),
            SpatialMath.overlapLengthMeters(b.path, a.path, MERGE_CORRIDOR_METERS),
        )
        return overlap >= shorter * MERGE_OVERLAP_RATIO
    }

    /**
     * 후보를 채점하고 **유형당 정확히 하나씩(최대 3개)** 골라 배정한다.
     * safe=최고점, fast=최소시간(safe와 다른 후보), main=남은 후보 중 최고점.
     * 후보가 3개 미만이면 있는 만큼만 반환한다(없는 유형을 억지로 만들지 않음 — 정직).
     * 동률은 먼저 나온 후보 우선(인덱스 미세 가중 — 프론트 scoreAndType 동형).
     */
    private fun scoreTypeAndLimit(candidates: List<RouteCandidate>): List<ScoredRoute> {
        if (candidates.isEmpty()) return emptyList()
        val scored = candidates.map { it to scorer.score(it.path) }

        // safe: 최고점(동률이면 먼저 나온 후보).
        val safeIdx = scored.indices.maxByOrNull { scored[it].second.score.toDouble() - it * TIE_BREAK_EPSILON }!!
        // fast: safe 제외 최소시간(동률이면 먼저 나온 후보).
        val fastIdx = scored.indices.filter { it != safeIdx }
            .minByOrNull { scored[it].first.timeS.toDouble() + it * TIE_BREAK_EPSILON }
        val used = setOfNotNull(safeIdx, fastIdx)
        // main: 남은 후보 중 최고점.
        val mainIdx = scored.indices.filter { it !in used }
            .maxByOrNull { scored[it].second.score.toDouble() - it * TIE_BREAK_EPSILON }

        val picks = LinkedHashMap<Int, String>()
        picks[safeIdx] = "safe"
        if (fastIdx != null) picks[fastIdx] = "fast"
        if (mainIdx != null) picks[mainIdx] = "main"
        return picks.map { (i, type) -> ScoredRoute(scored[i].first, scored[i].second, type) }
    }

    /** ScoredRoute → 프론트 계약 RouteOption. 안전 점수 내림차순 정렬(동률은 입력 순서 유지). */
    private fun toRouteOptions(
        scoredRoutes: List<ScoredRoute>,
        origin: GeoPoint,
        destination: GeoPoint,
    ): List<RouteOption> {
        val provenance = buildProvenance()
        return scoredRoutes
            .sortedByDescending { it.score.score }
            .mapIndexed { index, scoredRoute ->
                val candidate = scoredRoute.candidate
                val breakdown = scoredRoute.score.breakdown
                RouteOption(
                    id = "tmap-${candidate.searchOption}-$index",
                    name = TYPE_NAME.getValue(scoredRoute.type),
                    time = "${max(1, (candidate.timeS / 60.0).roundToInt())}분",
                    dist = RouteFormat.formatDistance(candidate.distanceM),
                    desc = "안전 점수 ${scoredRoute.score.score}점 · CCTV ${breakdown.cctvDensity}/km",
                    tags = buildTags(scoredRoute),
                    type = scoredRoute.type,
                    path = candidate.path,
                    steps = candidate.steps,
                    score = scoredRoute.score.score,
                    markers = buildMarkers(origin, destination, candidate.path),
                    // P0-4 점수 근거 — 프론트가 "CCTV n/km · 비상벨 n …"으로 그대로 렌더한다.
                    breakdown = ScoreBreakdown(
                        routeKm = breakdown.routeKm,
                        cctvDensity = breakdown.cctvDensity,
                        lampDensity = breakdown.lampDensity,
                        bellDensity = breakdown.bellDensity,
                        safehouseCount = breakdown.safehouseCount,
                        policeCount = breakdown.policeCount,
                        safePathOverlap = breakdown.safePathOverlap,
                    ),
                    provenance = provenance,
                )
            }
    }

    /**
     * 실데이터 출처/기준일(P0-1·P0-3). safe-compare 산출에만 붙는다(폴백엔 없음 → 프론트가 '예시'로 구분).
     * 기준일은 manifest generatedAt(ISO)의 연·월을 "YYYY.MM"로 표기(미상이면 생략).
     */
    private fun buildProvenance(): DataProvenance {
        val generatedAt = repository.freshness?.generatedAt
        val basedOn = generatedAt?.takeIf { it.length >= 7 }?.substring(0, 7)?.replace('-', '.')
        return DataProvenance(kind = "live", basedOn = basedOn, origin = DATA_ORIGIN)
    }

    private fun buildTags(scoredRoute: ScoredRoute): List<RouteTag> = buildList {
        if (scoredRoute.type == "safe") add(RouteTag("안심", "mint"))
        if (scoredRoute.type == "fast") add(RouteTag("빠른길", "blue"))
        val breakdown = scoredRoute.score.breakdown
        if (breakdown.cctvDensity >= 5) add(RouteTag("CCTV 많음", "mint"))
        if (breakdown.lampDensity >= 10) add(RouteTag("밝은 길", "yellow"))
        if (breakdown.safePathOverlap >= 0.2) add(RouteTag("안심귀갓길", "mint"))
        if (breakdown.safehouseCount >= 1) add(RouteTag("안심지킴이집", "outline"))
        if (breakdown.policeCount >= 1) add(RouteTag("지구대 인접", "blue"))
    }

    /**
     * 경로변 거점 마커: 출발/도착 + 회랑 내 CCTV/안심집/비상벨. 실지도용 lat/lng 는 그대로 두고,
     * MapMock 폴백용 정규화 x/y(0~100, 여백 12%)는 전 마커 경계상자에 투영한다(프론트 buildRouteMarkers 동형).
     */
    private fun buildMarkers(origin: GeoPoint, destination: GeoPoint, path: List<GeoPoint>): List<FacilityPoi> {
        val corridor = SafetyScorer.DEFAULT_CORRIDOR_METERS
        val raw = ArrayList<FacilityPoi>()
        raw.add(FacilityPoi(type = "start", x = 0, y = 0, lat = origin.lat, lng = origin.lng))
        raw.add(FacilityPoi(type = "end", x = 0, y = 0, lat = destination.lat, lng = destination.lng))
        for ((markerType, facilityType) in MARKER_TYPES) {
            // 유형별 마커 수 상한 — 가로등처럼 밀집한 시설이 지도를 가리지 않도록 방어(점수 계산은 전체 개수를 그대로 쓴다).
            for (facility in repository.index(facilityType).withinCorridor(path, corridor).take(MAX_MARKERS_PER_TYPE)) {
                raw.add(
                    FacilityPoi(
                        type = markerType,
                        x = 0,
                        y = 0,
                        lat = facility.lat,
                        lng = facility.lng,
                        name = facility.name,
                        purpose = facility.purpose,
                        cameraCount = facility.cameraCount,
                        address = facility.address,
                        phone = facility.phone,
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
        return raw.map { marker ->
            // 한 축이 퇴화(모든 좌표 동일)하면 가운데(50). 위도는 위쪽이 큰 값이라 y축을 뒤집는다.
            val x = if (spanLng == 0.0) 50 else (MARKER_PADDING + (marker.lng - minLng) / spanLng * range).roundToInt()
            val y = if (spanLat == 0.0) 50 else (MARKER_PADDING + (maxLat - marker.lat) / spanLat * range).roundToInt()
            marker.copy(x = x.coerceIn(0, 100), y = y.coerceIn(0, 100))
        }
    }

    private companion object {
        val TYPE_NAME = mapOf("safe" to "안심 경로", "main" to "기본 경로", "fast" to "빠른 경로")
        // 마커 표시 종류 → 시설 인덱스(조명은 점수에만 반영, 마커 제외 — 프론트 MVP 기본 마커 3종).
        val MARKER_TYPES = listOf(
            "cctv" to FacilityType.CCTV,
            "safehouse" to FacilityType.SAFEHOUSE,
            "bell" to FacilityType.BELL,
            // 지구대·파출소도 경로변 거점 마커로 표시한다(여성안심지킴이집과 함께 지도에 노출).
            "police" to FacilityType.POLICE,
            // 가로등·조명은 점수(밀도)에도 쓰이고, 지도에도 표시한다(상한으로 밀집 방어).
            "lamp" to FacilityType.LAMP,
        )
        const val MARKER_PADDING = 12.0

        /** 근접 경로 병합: 두 경로 점이 이 거리(m) 안이면 같은 도로 위로 본다. */
        const val MERGE_CORRIDOR_METERS = 25.0

        /** 근접 경로 병합 임계: 짧은 경로의 이 비율 이상이 다른 경로와 겹치면 같은 경로로 병합. */
        const val MERGE_OVERLAP_RATIO = 0.85

        /** 유형별 마커 수 상한(가로등 등 밀집 시설로 인한 지도 가독성 저하·응답 비대화 방어). */
        const val MAX_MARKERS_PER_TYPE = 12

        /** 동률 처리용 인덱스 미세 가중 — "먼저 나온 후보 우선"을 점수 비교 한 번으로 표현한다. */
        const val TIE_BREAK_EPSILON = 1e-9

        /** 안심시설 데이터 원천 표기(P0-3). 파이프라인 sources.yaml 의 원천 계열. */
        const val DATA_ORIGIN = "공공데이터포털·서울 열린데이터광장"
    }
}

/**
 * 채점 대상 도메인 경로 후보 — 클라이언트 [TmapRoute] 의 도메인 표현(좌표를 [GeoPoint] 로 매핑).
 * steps(단계 안내)는 값 그대로 응답에 실리므로 클라이언트 타입([NavStep])을 재사용한다.
 */
data class RouteCandidate(
    val searchOption: String,
    val path: List<GeoPoint>,
    val distanceM: Double,
    val timeS: Int,
    val steps: List<NavStep>,
)

private data class ScoredRoute(val candidate: RouteCandidate, val score: SafetyScore, val type: String)

private fun TmapRoute.toCandidate(): RouteCandidate =
    RouteCandidate(searchOption = searchOption, path = path.toGeoPoints(), distanceM = distanceM, timeS = timeS, steps = steps)

private fun List<TmapCoord>.toGeoPoints(): List<GeoPoint> = map { GeoPoint(it.lat, it.lng) }

private fun GeoPoint.toTmapCoord(): TmapCoord = TmapCoord(lat, lng)
