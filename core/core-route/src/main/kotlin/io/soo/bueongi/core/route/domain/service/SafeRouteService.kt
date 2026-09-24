package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.error.RouteSearchUnavailableException
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.domain.model.ScoredCandidate
import io.soo.bueongi.core.route.domain.model.ScoredRoute
import io.soo.bueongi.core.route.domain.safety.model.SafetyDataCoverage
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.sampleEveryMeters
import org.springframework.stereotype.Service

@Service
class SafeRouteService(
    private val routeFinder: RouteFinder,
    private val candidateMerger: RouteCandidateMerger,
    private val scorer: SafetyScorer,
    private val assembler: RouteOptionAssembler,
    private val visitEvents: RouteVisitEventPublisher,
) {
    fun safeCompare(query: RouteRequest): List<RouteOption> {
        if (!routeFinder.isEnabled) {
            throw RouteSearchUnavailableException()
        }
        val routes: ArrayList<TmapRoute> = ArrayList(routeFinder.findRoutes(query))
        if (routes.isEmpty()) {
            throw RouteSearchUnavailableException()
        }
        // 길을 잃어 다시 부른 것이면 빨리 답해야 해서 우회를 찾지 않는다.
        if (!query.reroute) {
            routes.addAll(routeFinder.findSaferRoutes(query, routes))
        }

        // 보안등은 자치단체마다 따로 요청해야 해서, 경로 위 여러 지점을 알려 그 지역 시설을 받아 오게 한다.
        val visitedPath: List<GeoPoint> = routes.first().path.toGeoPoints()
        visitEvents.routeVisited(visitedPath.sampleEveryMeters(FILL_SAMPLE_SPACING_METERS, MAX_FILL_SAMPLE_POINTS))

        val candidates: List<RouteCandidate> = candidateMerger.toCandidates(routes)
        val assigned: List<ScoredRoute> = assignOneRoutePerType(candidates)
        return assembler.assemble(assigned, query.origin, query.destination)
    }

    private fun assignOneRoutePerType(candidates: List<RouteCandidate>): List<ScoredRoute> {
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val coverage: SafetyDataCoverage = scorer.coverageOf(candidates.map { it.path })
        val scored: List<ScoredCandidate> = candidates.map { ScoredCandidate(it, scorer.score(it.path, coverage)) }

        val safeIdx: Int = scored.indices.maxBy { scored[it].score.rank.toDouble() - it * TIE_BREAK_EPSILON }
        val fastIdx: Int? =
            scored.indices.filter { it != safeIdx }
                .minByOrNull { scored[it].candidate.timeS.toDouble() + it * TIE_BREAK_EPSILON }
        val used: Set<Int> = setOfNotNull(safeIdx, fastIdx)
        val mainIdx: Int? =
            scored.indices.filter { it !in used }
                .maxByOrNull { scored[it].score.rank.toDouble() - it * TIE_BREAK_EPSILON }

        val picks: LinkedHashMap<Int, RouteType> = LinkedHashMap()
        picks[safeIdx] = RouteType.SAFE
        if (fastIdx != null) {
            picks[fastIdx] = RouteType.FAST
        }
        if (mainIdx != null) {
            picks[mainIdx] = RouteType.MAIN
        }
        val assigned: ArrayList<ScoredRoute> = ArrayList()
        for (pick in picks) {
            val candidate: ScoredCandidate = scored[pick.key]
            assigned.add(ScoredRoute(candidate.candidate, candidate.score, pick.value))
        }
        return assigned
    }

    private companion object {
        const val TIE_BREAK_EPSILON: Double = 1e-9

        const val FILL_SAMPLE_SPACING_METERS: Double = 2_000.0

        // 점 하나가 최악의 경우 보안등 약 8회 외부 호출이라 한 요청의 쿼터 소모를 이 상한으로 막는다.
        const val MAX_FILL_SAMPLE_POINTS: Int = 12
    }
}
