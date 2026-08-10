package io.soo.bueongi.domain.route

import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.ApiProvider
import io.soo.bueongi.domain.facility.SafeAnchor
import io.soo.bueongi.domain.facility.SafeDetourPlanner
import io.soo.bueongi.domain.facility.SafetyBreakdown
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

@Service
class SafeRouteService(
    private val routeService: RouteService,
    private val tmapClient: TmapRouteClient,
    private val scorer: SafetyScorer,
    private val detourPlanner: SafeDetourPlanner,
    private val repository: FacilitySpatialSource,
    private val markerBuilder: RouteMarkerBuilder,
    private val candidateMerger: RouteCandidateMerger,
    private val alertNotifier: AlertNotifier? = null,
    private val apiCallLogStore: ApiCallLogStore? = null,
    private val routeStatsStore: RouteStatsStore? = null,
) {

    fun safeCompare(query: RouteQuery): List<RouteOption> {
        routeStatsStore.recordRecommendation("safe-compare", query)

        if (!tmapClient.isEnabled) {
            return routeService.buildRoutes(query)
        }

        val origin: GeoPoint = query.origin
        val destination: GeoPoint = query.destination
        val budget: Double = query.safetyPreference.detourBudget

        // ponytail: service 레벨 계측 — HTTP statusCode 등 client 내부 상세는 후속. client 모듈 계측 훅(nullable recorder 주입)으로 승격 가능.
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
        if (base.isEmpty()) {
            alertNotifier?.notify(
                key = "tmap-route-fallback",
                title = "Tmap 경로 API 실패",
                detail = "모든 경로 후보 호출 실패 — 폴백 경로로 응답 중(키 설정 상태)",
            )
            return routeService.buildRoutes(query)
        }

        val baseShortestM: Double = base.minOf { it.distanceM }

        val directPath: List<GeoPoint> = base.first().path.toGeoPoints()
        val anchors: List<SafeAnchor> = detourPlanner.extractSafeAnchors(directPath)
        val waypointCombos: List<List<GeoPoint>> = detourPlanner.pickDetourWaypoints(origin, destination, anchors)
        val detours: List<TmapRoute> = waypointCombos.mapNotNull { waypoints ->
            tmapClient.fetchRouteVia(
                origin.toTmapCoord(),
                destination.toTmapCoord(),
                waypoints.map { it.toTmapCoord() },
                destName = query.destinationName,
            )
        }
            .filter { it.distanceM <= baseShortestM * (1 + budget) }

        val candidates: List<RouteCandidate> = candidateMerger.mergeNearDuplicates(
            dedupeByIdentity(base + detours).map { it.toCandidate() },
        )
        val scored: List<ScoredRoute> = scoreTypeAndLimit(candidates)
        return toRouteOptions(scored, origin, destination)
    }

    private fun dedupeByIdentity(routes: List<TmapRoute>): List<TmapRoute> {
        val seen: HashSet<Long> = HashSet<Long>()
        return routes.filter { seen.add(it.identityKey) }
    }

    private fun scoreTypeAndLimit(candidates: List<RouteCandidate>): List<ScoredRoute> {
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val scored: List<ScoredCandidate> = candidates.map { ScoredCandidate(it, scorer.score(it.path)) }

        val safeIdx: Int = scored.indices.maxBy { scored[it].score.score.toDouble() - it * TIE_BREAK_EPSILON }
        val fastIdx: Int? = scored.indices.filter { it != safeIdx }
            .minByOrNull { scored[it].candidate.timeS.toDouble() + it * TIE_BREAK_EPSILON }
        val used: Set<Int> = setOfNotNull(safeIdx, fastIdx)
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
                val breakdown: SafetyBreakdown = scoredRoute.score.breakdown
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
        val breakdown: SafetyBreakdown = scoredRoute.score.breakdown
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

        const val TIE_BREAK_EPSILON: Double = 1e-9

        const val DATA_ORIGIN: String = "공공데이터포털·서울 열린데이터광장"

        const val YEAR_MONTH_LENGTH: Int = 7

        const val TAG_CCTV_DENSITY_MIN_PER_KM: Double = 5.0

        const val TAG_LAMP_DENSITY_MIN_PER_KM: Double = 10.0

        const val TAG_SAFE_PATH_OVERLAP_MIN: Double = 0.2
    }
}




private fun TmapRoute.toCandidate(): RouteCandidate =
    RouteCandidate(searchOption = searchOption, path = path.toGeoPoints(), distanceM = distanceM, timeS = timeS, steps = steps)

private fun List<TmapCoord>.toGeoPoints(): List<GeoPoint> = map { GeoPoint(it.lat, it.lng) }

private fun GeoPoint.toTmapCoord(): TmapCoord = TmapCoord(lat, lng)
