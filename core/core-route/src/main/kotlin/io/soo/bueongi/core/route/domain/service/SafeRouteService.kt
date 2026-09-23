package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.model.ScoredCandidate
import io.soo.bueongi.core.route.domain.model.ScoredRoute
import io.soo.bueongi.core.route.domain.safety.model.SafetyDataCoverage
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

@Service
class SafeRouteService(
    private val routeService: RouteService,
    private val collector: RouteCandidateCollector,
    private val scorer: SafetyScorer,
    private val assembler: RouteOptionAssembler,
    private val routeEvents: RouteEventPublisher,
    private val routeStatsStore: RouteStatsStore,
) {
    fun safeCompare(query: RouteRequest): List<RouteOption> {
        if (!query.reroute) {
            routeStatsStore.record(
                RouteRecommendationLog(
                    atEpochMs = System.currentTimeMillis(),
                    endpoint = "safe-compare",
                    destinationName = query.destinationName,
                    preference = query.safetyPreference,
                ),
            )
        }

        if (!collector.isEnabled) {
            return routeService.buildStraightLineRoutes(query)
        }
        val base: List<TmapRoute> = collector.collectBase(query)
        if (base.isEmpty()) {
            routeEvents.safeRouteFellBack("모든 경로 후보 호출 실패 — 폴백 경로로 응답 중(키 설정 상태)")
            return routeService.buildStraightLineRoutes(query)
        }
        val assigned: List<ScoredRoute> = assignOneRoutePerType(collector.collectAll(query, base))
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
