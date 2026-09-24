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
import org.springframework.stereotype.Service

@Service
class SafeRouteService(
    private val routeFinder: RouteFinder,
    private val candidateMerger: RouteCandidateMerger,
    private val scorer: SafetyScorer,
    private val assembler: RouteOptionAssembler,
    private val routeEvents: RouteEventPublisher,
) {
    fun safeCompare(query: RouteRequest): List<RouteOption> {
        if (!routeFinder.isEnabled) {
            throw RouteSearchUnavailableException()
        }
        val routes: ArrayList<TmapRoute> = ArrayList(routeFinder.findRoutes(query))
        if (routes.isEmpty()) {
            routeEvents.safeRouteFellBack("모든 경로 후보 호출 실패 — 503 으로 알림")
            throw RouteSearchUnavailableException()
        }
        if (!query.reroute) {
            // 경로 이탈로 앱이 다시 부른 것은 우회를 찾지 않는다 — 길 잃은 사람에게는 빨리 답한다.
            routes.addAll(routeFinder.findSaferRoutes(query, routes))
        }

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
    }
}
