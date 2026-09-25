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
