package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.core.route.domain.model.ScoredCandidate
import io.soo.bueongi.core.route.domain.model.ScoredRoute
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

@Service
class SafeRouteService(
    private val routeService: RouteService,
    private val collector: RouteCandidateCollector,
    private val scorer: SafetyScorer,
    private val assembler: RouteOptionAssembler,
    // Tmap·통계가 없는 조립에서도 경로 비교는 돌아야 해서 선택 주입이다.
    private val routeEvents: RouteEventPublisher? = null,
    private val routeStatsStore: RouteStatsStore? = null,
) {

    fun safeCompare(query: RouteQuery): List<RouteOption> {
        // 폴백으로 빠지더라도 추천 집계는 남긴다.
        routeStatsStore.recordRecommendation("safe-compare", query)

        if (!collector.isEnabled) {
            return routeService.buildStraightLineRoutes(query)
        }
        val base: List<TmapRoute> = collector.collectBase(query)
        if (base.isEmpty()) {
            routeEvents?.safeRouteFellBack("모든 경로 후보 호출 실패 — 폴백 경로로 응답 중(키 설정 상태)")
            return routeService.buildStraightLineRoutes(query)
        }
        val assigned: List<ScoredRoute> = assignOneRoutePerType(collector.collectAll(query, base))
        return assembler.assemble(assigned, query.origin, query.destination)
    }

    private fun assignOneRoutePerType(candidates: List<RouteCandidate>): List<ScoredRoute> {
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

        /** 동점이면 앞선 후보가 이기게 하는 기울기 — 같은 입력에 같은 결과를 보장한다. */
        const val TIE_BREAK_EPSILON: Double = 1e-9
    }
}
