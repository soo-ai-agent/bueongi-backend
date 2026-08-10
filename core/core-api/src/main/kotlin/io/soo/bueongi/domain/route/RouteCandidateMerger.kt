package io.soo.bueongi.domain.route

import io.soo.bueongi.storage.db.core.facility.SpatialMath
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
        val lengthFirst: Double = SpatialMath.pathLengthMeters(first.path)
        val lengthSecond: Double = SpatialMath.pathLengthMeters(second.path)
        val shorter: Double = minOf(lengthFirst, lengthSecond)
        if (shorter <= 0.0) {
            return false
        }
        val overlap: Double = maxOf(
            SpatialMath.overlapLengthMeters(first.path, second.path, MERGE_CORRIDOR_METERS),
            SpatialMath.overlapLengthMeters(second.path, first.path, MERGE_CORRIDOR_METERS),
        )
        return overlap >= shorter * MERGE_OVERLAP_RATIO
    }

    private companion object {
        const val MERGE_CORRIDOR_METERS: Double = 25.0

        const val MERGE_OVERLAP_RATIO: Double = 0.85
    }
}
