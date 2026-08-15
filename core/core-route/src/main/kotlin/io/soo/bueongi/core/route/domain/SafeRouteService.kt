package io.soo.bueongi.core.route.domain

import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.core.route.domain.safety.SafetyScorer
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.springframework.stereotype.Service

/**
 * 안심 경로 비교 — 후보 수집([collector]) → 채점·유형 배정 → 응답 조립([assembler]) 순서를 잇는다.
 * Tmap 이 없거나 후보가 전멸하면 [routeService] 폴백으로 돌린다.
 */
@Service
class SafeRouteService(
    private val routeService: RouteService,
    private val collector: RouteCandidateCollector,
    private val scorer: SafetyScorer,
    private val assembler: RouteOptionAssembler,
    private val alertNotifier: AlertNotifier? = null,
    private val routeStatsStore: RouteStatsStore? = null,
) {

    fun safeCompare(query: RouteQuery): List<RouteOption> {
        routeStatsStore.recordRecommendation("safe-compare", query)

        if (!collector.isEnabled) {
            return routeService.buildRoutes(query)
        }

        val base: List<TmapRoute> = collector.collectBase(query)
        if (base.isEmpty()) {
            alertNotifier?.notify(
                key = "tmap-route-fallback",
                title = "Tmap 경로 API 실패",
                detail = "모든 경로 후보 호출 실패 — 폴백 경로로 응답 중(키 설정 상태)",
            )
            return routeService.buildRoutes(query)
        }

        val scored: List<ScoredRoute> = scoreTypeAndLimit(collector.collectAll(query, base))
        return assembler.assemble(scored, query.origin, query.destination)
    }

    /** 후보를 채점해 안심·최단·주요 각 하나씩 배정한다. 동점은 앞선 후보가 이긴다. */
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

    private companion object {

        const val TIE_BREAK_EPSILON: Double = 1e-9
    }
}
