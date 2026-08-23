package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.storage.db.core.safetyspot.model.overlapMetersWith
import io.soo.bueongi.storage.db.core.safetyspot.model.pathLengthMeters
import org.springframework.stereotype.Component

/** Tmap 은 옵션만 다르면 골목 하나 차이의 길을 여러 번 준다 — 그대로 두면 같아 보이는 선택지가 3개 뜬다. */
@Component
class RouteCandidateMerger {

    /** 먼저 온 후보를 남긴다 — Tmap 이 더 추천한 쪽이다. */
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
        // 겹침은 방향에 따라 다르게 나온다(A가 B를 따라가는 정도 ≠ 그 반대) → 큰 쪽을 쓴다.
        val overlap: Double = maxOf(
            first.path.overlapMetersWith(second.path, MERGE_CORRIDOR_METERS),
            second.path.overlapMetersWith(first.path, MERGE_CORRIDOR_METERS),
        )
        return overlap >= shorter * MERGE_OVERLAP_RATIO
    }

    private companion object {

        /**
         * 겹침을 재는 폭. 25m 였을 때 **나란한 골목 두 개가 같은 길로 셈됐다** —
         * 신촌역→이대역 실측에서 세 후보의 겹침이 25m 로는 0.96·0.96 이라 하나로 합쳐져 경로가 1개만
         * 남았는데, 15m 로 재면 0.89·0.88 로 갈린다(서울 도심 이면도로 간격이 대략 20m 다).
         * 두 값의 차이가 곧 "20m 옆 다른 골목을 같은 길로 볼 것인가" 이고, 조명·CCTV 는 골목마다 다르다.
         */
        const val MERGE_CORRIDOR_METERS: Double = 15.0

        /**
         * 이 비율 이상 겹치면 같은 길로 본다. 0.85 는 **다른 길까지 합쳐 선택지를 없앴다** —
         * 강남역→홍대입구 실측에서 후보 6개가 2개로, 신촌역→이대역은 3개가 1개로 줄었다.
         * 0.90 + 회랑 15m 로 재면 각각 4개·3개가 남고, 남은 후보끼리 서로 다른 구간이
         * 최소 98m(1km 경로)·1,453m(13km 경로) 라 화면에서 실제로 다른 선을 그린다.
         * 더 올리면(0.95) 13km 경로에서 1.3km 만 다른 사실상 같은 길이 따로 뜬다.
         */
        const val MERGE_OVERLAP_RATIO: Double = 0.90
    }
}
