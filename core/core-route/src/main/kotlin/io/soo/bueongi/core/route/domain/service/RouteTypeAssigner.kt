package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.ScoredCandidate
import io.soo.bueongi.core.route.domain.model.ScoredRoute
import io.soo.bueongi.core.route.domain.safety.model.SafetyDataCoverage
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import org.springframework.stereotype.Component

// 후보마다 안전 점수를 매기고, 앱에 보여줄 유형(안심·빠름·일반)마다 경로 하나씩 고른다.
@Component
class RouteTypeAssigner(
    private val scorer: SafetyScorer,
) {
    internal fun assignOneRoutePerType(candidates: List<RouteCandidate>): List<ScoredRoute> {
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val coverage: SafetyDataCoverage = scorer.coverageOf(candidates.map { it.path })
        val scoredCandidates: List<ScoredCandidate> = candidates.map { ScoredCandidate(it, scorer.score(it.path, coverage)) }

        val picks: Map<Int, RouteType> = pickIndexPerType(scoredCandidates)
        val scoredRoutes: ArrayList<ScoredRoute> = ArrayList()
        for (pick in picks) {
            val candidate: ScoredCandidate = scoredCandidates[pick.key]
            scoredRoutes.add(ScoredRoute(candidate.candidate, candidate.score, pick.value))
        }
        return scoredRoutes
    }

    // 안심=점수 최고, 빠름=남은 것 중 시간 최단, 기본=남은 비우회 후보 중 점수 최고. 동점이면 앞선 후보가 이긴다.
    // 우회 후보는 경유지를 강제해 모양이 꼬일 수 있어 안심 자리 외에는 쓰지 않는다.
    private fun pickIndexPerType(scoredCandidates: List<ScoredCandidate>): Map<Int, RouteType> {
        val safeIdx: Int = scoredCandidates.indices.maxBy { scoredCandidates[it].score.rank.toDouble() - it * TIE_BREAK_EPSILON }
        val fastIdx: Int? =
            scoredCandidates.indices.filter { it != safeIdx }
                .minByOrNull { scoredCandidates[it].candidate.timeS.toDouble() + it * TIE_BREAK_EPSILON }
        val used: Set<Int> = setOfNotNull(safeIdx, fastIdx)
        val mainIdx: Int? =
            scoredCandidates.indices.filter { it !in used && !scoredCandidates[it].candidate.isDetour }
                .maxByOrNull { scoredCandidates[it].score.rank.toDouble() - it * TIE_BREAK_EPSILON }

        val picks: LinkedHashMap<Int, RouteType> = LinkedHashMap()
        picks[safeIdx] = RouteType.SAFE
        if (fastIdx != null) {
            picks[fastIdx] = RouteType.FAST
        }
        if (mainIdx != null) {
            picks[mainIdx] = RouteType.MAIN
        }
        return picks
    }

    private companion object {
        const val TIE_BREAK_EPSILON: Double = 1e-9
    }
}
