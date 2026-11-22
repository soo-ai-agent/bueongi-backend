package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.storage.db.safetyspot.model.overlapMetersWith
import io.soo.bueongi.storage.db.safetyspot.model.pathLengthMeters
import org.springframework.stereotype.Component

@Component
class RouteCandidateMerger {
    // 같은 경로(identityKey)와 거의 같은 경로(90% 이상 겹침)를 버린다.
    fun merge(routeList: List<RouteCandidate>): List<RouteCandidate> {
        val addedKeys: HashSet<Long> = HashSet()
        val kept: ArrayList<RouteCandidate> = ArrayList()
        for (candidate in routeList) {
            if (!addedKeys.add(candidate.identityKey)) {
                continue
            }
            val candidateLength: Double = candidate.path.pathLengthMeters()
            var isNearDuplicate = false
            for (keptCandidate in kept) {
                val shorter: Double = minOf(keptCandidate.path.pathLengthMeters(), candidateLength)
                if (shorter <= 0.0) {
                    continue
                }
                val overlap: Double =
                    maxOf(
                        keptCandidate.path.overlapMetersWith(candidate.path, MERGE_CORRIDOR_METERS),
                        candidate.path.overlapMetersWith(keptCandidate.path, MERGE_CORRIDOR_METERS),
                    )
                if (overlap >= shorter * MERGE_OVERLAP_RATIO) {
                    isNearDuplicate = true
                    break
                }
            }
            if (!isNearDuplicate) {
                kept.add(candidate)
            }
        }
        return kept
    }

    private companion object {
        // 서울 도심 이면도로 간격이 약 20m 라, 나란한 골목을 한 길로 뭉치지 않으려면 그보다 좁아야 한다.
        const val MERGE_CORRIDOR_METERS: Double = 15.0
        const val MERGE_OVERLAP_RATIO: Double = 0.90
    }
}
