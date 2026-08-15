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
    private val routeEvents: RouteEventPublisher? = null,
    private val routeStatsStore: RouteStatsStore? = null,
) {

    fun safeCompare(query: RouteQuery): List<RouteOption> {
        // 1. 어떤 선호로 몇 번 추천됐는지 먼저 남긴다(폴백으로 빠져도 집계는 해야 한다).
        routeStatsStore.recordRecommendation("safe-compare", query)

        // 2. Tmap 키가 없으면 실제 경로를 못 받는다 → 직선거리 기반 안내용 경로로 대체.
        if (!collector.isEnabled) {
            return routeService.buildRoutes(query)
        }

        // 3. 기본 후보(추천·대로우선·최단)를 받는다. 전멸이면 Tmap 장애로 보고 알린 뒤 폴백.
        val base: List<TmapRoute> = collector.collectBase(query)
        if (base.isEmpty()) {
            routeEvents?.safeRouteFellBack("모든 경로 후보 호출 실패 — 폴백 경로로 응답 중(키 설정 상태)")
            return routeService.buildRoutes(query)
        }

        // 4. 기본 후보 + 안심 우회 후보를 모아 채점하고, 유형(안심·최단·주요)을 하나씩 배정한다.
        val scored: List<ScoredRoute> = scoreTypeAndLimit(collector.collectAll(query, base))

        // 5. 응답 모양(표기·태그·마커·출처)으로 옮긴다.
        return assembler.assemble(scored, query.origin, query.destination)
    }

    /** 후보를 채점해 안심·최단·주요 각 하나씩 배정한다. 동점은 앞선 후보가 이긴다. */
    private fun scoreTypeAndLimit(candidates: List<RouteCandidate>): List<ScoredRoute> {
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val scored: List<ScoredCandidate> = candidates.map { ScoredCandidate(it, scorer.score(it.path)) }

        // 안심 = 점수 1등, 최단 = 그 다음 중 소요시간 최소, 주요 = 남은 것 중 점수 최고.
        // TIE_BREAK_EPSILON 은 동점일 때 앞선 후보가 이기게 하는 아주 작은 기울기다(결과 고정).
        val safeIdx: Int = scored.indices.maxBy { scored[it].score.score.toDouble() - it * TIE_BREAK_EPSILON }
        val fastIdx: Int? = scored.indices.filter { it != safeIdx }
            .minByOrNull { scored[it].candidate.timeS.toDouble() + it * TIE_BREAK_EPSILON }
        val used: Set<Int> = setOfNotNull(safeIdx, fastIdx)
        val mainIdx: Int? = scored.indices.filter { it !in used }
            .maxByOrNull { scored[it].score.score.toDouble() - it * TIE_BREAK_EPSILON }

        // 후보가 1~2개뿐이면 배정 못 한 유형은 그냥 빠진다(억지로 채우지 않는다).
        val picks: LinkedHashMap<Int, RouteType> = LinkedHashMap<Int, RouteType>()
        picks[safeIdx] = RouteType.SAFE
        if (fastIdx != null) {
            picks[fastIdx] = RouteType.FAST
        }
        if (mainIdx != null) {
            picks[mainIdx] = RouteType.MAIN
        }
        val assigned: ArrayList<ScoredRoute> = ArrayList<ScoredRoute>()
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
