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
        for ((index, type) in picks) {
            val candidate: ScoredCandidate = scoredCandidates[index]
            scoredRoutes.add(ScoredRoute(candidate.candidate, candidate.score, type))
        }
        return scoredRoutes
    }

    // 안심=점수 최고, 빠름=남은 것 중 시간 최단, 기본=남은 비우회 후보 중 점수 최고. 동점이면 앞선 후보가 이긴다.
    // 우회 후보는 경유지를 강제해 모양이 꼬일 수 있어 기본 자리에는 쓰지 않는다.
    private fun pickIndexPerType(scoredCandidates: List<ScoredCandidate>): Map<Int, RouteType> {
        var safeIndex: Int = 0
        for (index in scoredCandidates.indices) {
            if (scoredCandidates[index].score.rank > scoredCandidates[safeIndex].score.rank) {
                safeIndex = index
            }
        }

        var fastIndex: Int? = null
        for (index in scoredCandidates.indices) {
            if (index == safeIndex) {
                continue
            }
            if (fastIndex == null || scoredCandidates[index].candidate.timeS < scoredCandidates[fastIndex].candidate.timeS) {
                fastIndex = index
            }
        }

        var mainIndex: Int? = null
        for (index in scoredCandidates.indices) {
            if (index == safeIndex || index == fastIndex || scoredCandidates[index].candidate.isDetour) {
                continue
            }
            if (mainIndex == null || scoredCandidates[index].score.rank > scoredCandidates[mainIndex].score.rank) {
                mainIndex = index
            }
        }

        val picks: LinkedHashMap<Int, RouteType> = LinkedHashMap()
        picks[safeIndex] = RouteType.SAFE
        if (fastIndex != null) {
            picks[fastIndex] = RouteType.FAST
        }
        if (mainIndex != null) {
            picks[mainIndex] = RouteType.MAIN
        }
        return picks
    }
}
