package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.storage.db.core.safetyspot.model.overlapMetersWith
import io.soo.bueongi.storage.db.core.safetyspot.model.pathLengthMeters
import org.springframework.stereotype.Component

// Tmap 이 옵션만 다르면 골목 하나 차이의 길을 중복해서 돌려준다.
@Component
class RouteCandidateMerger {

    fun mergeNearDuplicates(candidates: List<RouteCandidate>): List<RouteCandidate> {
        val kept: ArrayList<RouteCandidate> = ArrayList()
        for (candidate in candidates) {
            if (kept.none { isNearDuplicate(it, candidate) }) {
                kept.add(candidate)
            }
        }
        return kept
    }

    private fun isNearDuplicate(first: RouteCandidate, second: RouteCandidate): Boolean {
        val lengthFirst: Double = first.path.pathLengthMeters()
        val lengthSecond: Double = second.path.pathLengthMeters()
        val shorter: Double = minOf(lengthFirst, lengthSecond)
        if (shorter <= 0.0) {
            return false
        }
        val overlap: Double = maxOf(
            first.path.overlapMetersWith(second.path, MERGE_CORRIDOR_METERS),
            second.path.overlapMetersWith(first.path, MERGE_CORRIDOR_METERS),
        )
        return overlap >= shorter * MERGE_OVERLAP_RATIO
    }

    private companion object {

        // 서울 도심 이면도로 간격이 약 20m 라, 나란한 골목을 한 길로 뭉치지 않으려면 그보다 좁아야 한다.
        const val MERGE_CORRIDOR_METERS: Double = 15.0

        const val MERGE_OVERLAP_RATIO: Double = 0.90
    }
}
