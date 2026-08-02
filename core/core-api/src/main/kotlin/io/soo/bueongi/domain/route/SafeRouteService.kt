package io.soo.bueongi.domain.route

import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.ApiProvider
import io.soo.bueongi.domain.facility.SafeDetourPlanner
import io.soo.bueongi.domain.facility.SafetyScore
import io.soo.bueongi.domain.facility.SafetyScorer
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import io.soo.bueongi.storage.db.core.apilog.measure
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.storage.db.core.facility.GeoPoint
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
    private val markerBuilder: RouteMarkerBuilder,
    private val candidateMerger: RouteCandidateMerger,
    /** 운영 알림(없으면 null 무해) — Tmap 키가 있는데도 전부 실패해 폴백으로 전환될 때만 쓴다. */
    private val alertNotifier: AlertNotifier? = null,
    /** 외부 API 접속 내역 계측(없으면 null 무해) — 순수 부수효과, 경로 산출은 불변. */
    private val apiCallLogStore: ApiCallLogStore? = null,
    /** 추천 통계 집계(없으면 null 무해) — 순수 부수효과, 경로 산출은 불변. */
    private val routeStatsStore: RouteStatsStore? = null,
) {

    fun safeCompare(query: RouteQuery): List<RouteOption> {
        // 추천 요청 집계(safe-compare)는 진입 시 정확히 1회만 기록한다 — 아래 폴백은 routeService.buildRoutes(집계 없음)를
        // 호출하므로 이중 카운트가 나지 않는다(compare 로 폴백하면 compare 로도 한 번 더 집계되는 문제를 막는다).
        routeStatsStore.recordRecommendation("safe-compare", query)

        // Tmap 미설정이면 기존 결정론적 폴백(점수/마커 없는 RouteOption).
        if (!tmapClient.isEnabled) {
            return routeService.buildRoutes(query)
        }

        val origin: GeoPoint = query.origin
        val destination: GeoPoint = query.destination
        val budget: Double = query.safetyPreference.detourBudget

        // ponytail: service 레벨 계측 — HTTP statusCode 등 client 내부 상세는 후속. client 모듈 계측 훅(nullable recorder 주입)으로 승격 가능.
        // 경유(fetchRouteVia) 다건 호출은 개별 계측하지 않는다(요청당 다수라 로그 폭주 방지) — 대표 fetchRoutes 만 기록.
        val base: List<TmapRoute> = apiCallLogStore.measure(
            provider = ApiProvider.TMAP,
            endpoint = "route",
            success = { it.isNotEmpty() },
            detail = {
                if (it.isEmpty()) {
                    "경로 후보 0건 — 폴백"
                } else {
                    null
                }
            },
        ) {
            tmapClient.fetchRoutes(origin.toTmapCoord(), destination.toTmapCoord(), query.destinationName)
        }
        // Tmap 전부 실패 → 폴백(추천 끊김 방지). 키가 설정된 상태의 전면 실패는 운영 이상 신호라 알린다.
        if (base.isEmpty()) {
            alertNotifier?.notify(
                key = "tmap-route-fallback",
                title = "Tmap 경로 API 실패",
                detail = "모든 경로 후보 호출 실패 — 폴백 경로로 응답 중(키 설정 상태)",
            )
            return routeService.buildRoutes(query)
        }

        val baseShortestM: Double = base.minOf { it.distanceM }

        // 안심 경유지 우회 후보: 추천 경로 주변 시설 앵커 → 경유지 조합 → Tmap 경유 호출.
        val directPath: List<GeoPoint> = base.first().path.toGeoPoints()
        val anchors = detourPlanner.extractSafeAnchors(directPath)
        val waypointCombos: List<List<GeoPoint>> = detourPlanner.pickDetourWaypoints(origin, destination, anchors)
        val detours: List<TmapRoute> = waypointCombos.mapNotNull { waypoints ->
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
        val candidates: List<RouteCandidate> = candidateMerger.mergeNearDuplicates(
            dedupeByIdentity(base + detours).map { it.toCandidate() },
        )
        val scored: List<ScoredRoute> = scoreTypeAndLimit(candidates)
        return toRouteOptions(scored, origin, destination)
    }

    /** 같은 경로([TmapRoute.identityKey] 동일) 중복 제거 — 기본+우회 합류 시. 판정 키는 클라이언트와 단일 출처. */
    private fun dedupeByIdentity(routes: List<TmapRoute>): List<TmapRoute> {
        val seen: HashSet<Long> = HashSet<Long>()
        return routes.filter { seen.add(it.identityKey) }
    }

    /**
     * 후보를 채점하고 **유형당 정확히 하나씩(최대 3개)** 골라 배정한다.
     * safe=최고점, fast=최소시간(safe와 다른 후보), main=남은 후보 중 최고점.
     * 후보가 3개 미만이면 있는 만큼만 반환한다(없는 유형을 억지로 만들지 않음 — 정직).
     * 동률은 먼저 나온 후보 우선(인덱스 미세 가중 — 프론트 scoreAndType 동형).
     */
    private fun scoreTypeAndLimit(candidates: List<RouteCandidate>): List<ScoredRoute> {
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val scored: List<ScoredCandidate> = candidates.map { ScoredCandidate(it, scorer.score(it.path)) }

        // safe: 최고점(동률이면 먼저 나온 후보). 위 isEmpty 가드로 비어있지 않음이 보장되므로 비-널 maxBy.
        val safeIdx: Int = scored.indices.maxBy { scored[it].score.score.toDouble() - it * TIE_BREAK_EPSILON }
        // fast: safe 제외 최소시간(동률이면 먼저 나온 후보).
        val fastIdx: Int? = scored.indices.filter { it != safeIdx }
            .minByOrNull { scored[it].candidate.timeS.toDouble() + it * TIE_BREAK_EPSILON }
        val used: Set<Int> = setOfNotNull(safeIdx, fastIdx)
        // main: 남은 후보 중 최고점.
        val mainIdx: Int? = scored.indices.filter { it !in used }
            .maxByOrNull { scored[it].score.score.toDouble() - it * TIE_BREAK_EPSILON }

        val picks: LinkedHashMap<Int, RouteType> = LinkedHashMap<Int, RouteType>()
        picks[safeIdx] = RouteType.SAFE
        if (fastIdx != null) {
            picks[fastIdx] = RouteType.FAST
        }
        if (mainIdx != null) {
            picks[mainIdx] = RouteType.MAIN
        }
        return picks.map { (i, type) -> ScoredRoute(scored[i].candidate, scored[i].score, type) }
    }

    /** ScoredRoute → 프론트 계약 RouteOption. 안전 점수 내림차순 정렬(동률은 입력 순서 유지). */
    private fun toRouteOptions(
        scoredRoutes: List<ScoredRoute>,
        origin: GeoPoint,
        destination: GeoPoint,
    ): List<RouteOption> {
        val provenance: DataProvenance = buildProvenance()
        return scoredRoutes
            .sortedByDescending { it.score.score }
            .mapIndexed { index, scoredRoute ->
                val candidate: RouteCandidate = scoredRoute.candidate
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
                    markers = markerBuilder.buildMarkers(
                        origin,
                        destination,
                        candidate.path,
                    ),
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
        val generatedAt: String? = repository.freshness?.generatedAt
        val basedOn: String? = if (generatedAt != null && generatedAt.length >= YEAR_MONTH_LENGTH) {
            generatedAt.substring(0, YEAR_MONTH_LENGTH).replace('-', '.')
        } else {
            null
        }
        return DataProvenance(kind = "live", basedOn = basedOn, origin = DATA_ORIGIN)
    }

    private fun buildTags(scoredRoute: ScoredRoute): List<RouteTag> = buildList {
        if (scoredRoute.type == RouteType.SAFE) {
            add(RouteTag("안심", RouteTagVariant.MINT))
        }
        if (scoredRoute.type == RouteType.FAST) {
            add(RouteTag("빠른길", RouteTagVariant.BLUE))
        }
        val breakdown = scoredRoute.score.breakdown
        if (breakdown.cctvDensity >= TAG_CCTV_DENSITY_MIN_PER_KM) {
            add(RouteTag("CCTV 많음", RouteTagVariant.MINT))
        }
        if (breakdown.lampDensity >= TAG_LAMP_DENSITY_MIN_PER_KM) {
            add(RouteTag("밝은 길", RouteTagVariant.YELLOW))
        }
        if (breakdown.safePathOverlap >= TAG_SAFE_PATH_OVERLAP_MIN) {
            add(RouteTag("안심귀갓길", RouteTagVariant.MINT))
        }
        if (breakdown.safehouseCount >= 1) {
            add(RouteTag("안심지킴이집", RouteTagVariant.OUTLINE))
        }
        if (breakdown.policeCount >= 1) {
            add(RouteTag("지구대 인접", RouteTagVariant.BLUE))
        }
    }

    private companion object {
        val TYPE_NAME: Map<RouteType, String> = mapOf(RouteType.SAFE to "안심 경로", RouteType.MAIN to "기본 경로", RouteType.FAST to "빠른 경로")

        /** 동률 처리용 인덱스 미세 가중 — "먼저 나온 후보 우선"을 점수 비교 한 번으로 표현한다. */
        const val TIE_BREAK_EPSILON: Double = 1e-9

        /** 안심시설 데이터 원천 표기(P0-3). 파이프라인 sources.yaml 의 원천 계열. */
        const val DATA_ORIGIN: String = "공공데이터포털·서울 열린데이터광장"

        /** 기준일 표기용 "YYYY-MM" 길이 — generatedAt(ISO)에서 연·월만 잘라 쓴다. */
        const val YEAR_MONTH_LENGTH: Int = 7

        // 태그 부여 임계(MVP 캘리브레이션) — 점수 SAT_* 상수와 별개로, "표시할 만큼 두드러진가"의 기준.
        /** "CCTV 많음" 태그: 회랑 내 CCTV 밀도(개/km)가 이 값 이상. */
        const val TAG_CCTV_DENSITY_MIN_PER_KM: Double = 5.0

        /** "밝은 길" 태그: 가로등 밀도(개/km)가 이 값 이상. */
        const val TAG_LAMP_DENSITY_MIN_PER_KM: Double = 10.0

        /** "안심귀갓길" 태그: 경로 길이 대비 안심귀갓길 겹침 비율(0~1)이 이 값 이상. */
        const val TAG_SAFE_PATH_OVERLAP_MIN: Double = 0.2
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

/** 채점만 끝난 후보(유형 배정 전 중간 표현) — 안전점수·시간으로 safe/fast/main 을 고르는 입력. */
private data class ScoredCandidate(val candidate: RouteCandidate, val score: SafetyScore)

/**
 * 채점·유형 배정이 끝난 후보(응답 변환 직전의 중간 표현).
 * [type] 은 [RouteType] — TYPE_NAME 키 집합(전 유형)과 일치한다(toRouteOptions 의 getValue 전제).
 */
private data class ScoredRoute(val candidate: RouteCandidate, val score: SafetyScore, val type: RouteType)

private fun TmapRoute.toCandidate(): RouteCandidate =
    RouteCandidate(searchOption = searchOption, path = path.toGeoPoints(), distanceM = distanceM, timeS = timeS, steps = steps)

private fun List<TmapCoord>.toGeoPoints(): List<GeoPoint> = map { GeoPoint(it.lat, it.lng) }

private fun GeoPoint.toTmapCoord(): TmapCoord = TmapCoord(lat, lng)
