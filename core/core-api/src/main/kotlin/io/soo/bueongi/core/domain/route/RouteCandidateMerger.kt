package io.soo.bueongi.core.domain.route

import io.soo.bueongi.storage.db.core.facility.overlapMetersWith
import io.soo.bueongi.storage.db.core.facility.pathLengthMeters
import org.springframework.stereotype.Component

@Component
class RouteCandidateMerger {

    fun mergeNearDuplicates(candidates: List<RouteCandidate>): List<RouteCandidate> {
        val kept: ArrayList<RouteCandidate> = ArrayList<RouteCandidate>()
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
        const val MERGE_CORRIDOR_METERS: Double = 25.0

        const val MERGE_OVERLAP_RATIO: Double = 0.85
    }
}
