package io.soo.bueongi.domain.route

import io.soo.bueongi.storage.db.core.facility.SpatialMath
import org.springframework.stereotype.Component

/**
 * 근접 경로 병합 구현체 — 거의 같은 길인데 라벨만 다른 후보를 하나로 합친다
 * (예: 최단과 대로우선이 같은 도로를 90%+ 공유). 기하 겹침 판정이라는 세부 계산을
 * 이 구현체에 격리해 [SafeRouteService] 는 순서 지휘만 남긴다(Service=지휘자 규칙).
 *
 * 두 경로의 겹침 길이가 (짧은 쪽 길이 × [MERGE_OVERLAP_RATIO]) 이상이면 같은 경로로 보고,
 * 입력 순서상 먼저 나온 후보(=기본 추천/대로/최단 → 우회 순)를 남긴다. 결정론적.
 * 이렇게 해야 뒤의 유형 배정(safe/main/fast)이 서로 다른 실제 경로에만 매겨진다.
 */
@Component
class RouteCandidateMerger {

    /** 입력 순서를 보존하며 근접 중복 후보를 제거한 목록을 돌려준다. */
    fun mergeNearDuplicates(candidates: List<RouteCandidate>): List<RouteCandidate> {
        val kept = ArrayList<RouteCandidate>()
        for (candidate in candidates) {
            if (kept.none { isNearDuplicate(it, candidate) }) {
                kept.add(candidate)
            }
        }
        return kept
    }

    private fun isNearDuplicate(a: RouteCandidate, b: RouteCandidate): Boolean {
        val lengthA = SpatialMath.pathLengthMeters(a.path)
        val lengthB = SpatialMath.pathLengthMeters(b.path)
        val shorter = minOf(lengthA, lengthB)
        if (shorter <= 0.0) {
            return false
        }
        // 양방향 겹침 중 큰 값 — 한 경로가 다른 경로에 거의 포함되면 같은 길로 본다.
        val overlap = maxOf(
            SpatialMath.overlapLengthMeters(a.path, b.path, MERGE_CORRIDOR_METERS),
            SpatialMath.overlapLengthMeters(b.path, a.path, MERGE_CORRIDOR_METERS),
        )
        return overlap >= shorter * MERGE_OVERLAP_RATIO
    }

    private companion object {
        /** 근접 경로 병합: 두 경로 점이 이 거리(m) 안이면 같은 도로 위로 본다. */
        const val MERGE_CORRIDOR_METERS = 25.0

        /** 근접 경로 병합 임계: 짧은 경로의 이 비율 이상이 다른 경로와 겹치면 같은 경로로 병합. */
        const val MERGE_OVERLAP_RATIO = 0.85
    }
}
