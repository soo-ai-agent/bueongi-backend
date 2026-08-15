package io.soo.bueongi.core.route.domain

import io.soo.bueongi.storage.db.core.facility.overlapMetersWith
import io.soo.bueongi.storage.db.core.facility.pathLengthMeters
import org.springframework.stereotype.Component

/**
 * "사실상 같은 길"을 하나로 줄인다. Tmap 은 검색 옵션만 다르면 골목 하나 차이의 길을
 * 여러 번 돌려주는데, 그대로 두면 사용자에게 똑같아 보이는 선택지가 3개 뜬다.
 */
@Component
class RouteCandidateMerger {

    /** 앞선 후보와 겹치면 버린다 — 먼저 온 후보(= Tmap 이 더 추천한 쪽)를 남긴다. */
    fun mergeNearDuplicates(candidates: List<RouteCandidate>): List<RouteCandidate> {
        val kept: ArrayList<RouteCandidate> = ArrayList<RouteCandidate>()
        for (candidate in candidates) {
            if (kept.none { isNearDuplicate(it, candidate) }) {
                kept.add(candidate)
            }
        }
        return kept
    }

    /** 짧은 쪽 길이의 [MERGE_OVERLAP_RATIO] 이상이 겹치면 같은 길로 본다. */
    private fun isNearDuplicate(first: RouteCandidate, second: RouteCandidate): Boolean {
        val lengthFirst: Double = first.path.pathLengthMeters()
        val lengthSecond: Double = second.path.pathLengthMeters()
        val shorter: Double = minOf(lengthFirst, lengthSecond)
        if (shorter <= 0.0) {
            return false
        }
        // 겹침은 방향에 따라 다르게 나온다(A가 B를 따라가는 정도 ≠ 그 반대) → 큰 쪽을 쓴다.
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
