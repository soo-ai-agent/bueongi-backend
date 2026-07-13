package io.soo.bueongi.domain.admin

import kotlin.math.ceil

/**
 * 정렬된 표본에서 백분위수를 뽑는 순수 함수 — 관리자 지표의 소요시간 분위수(p50/p95/p99)를
 * [RequestMetricsService](수신 트래픽)·[ApiStatusService](외부 API)가 **같은 정의**로 계산하도록 단일 출처로 둔다.
 *
 * 정의가 서비스마다 갈라지면 같은 화면의 수치가 미묘하게 어긋나므로, 계산은 여기 한 곳에만 존재한다.
 */
object Percentiles {

    /**
     * nearest-rank 백분위수 — 오름차순 정렬된 [sortedAscending] 에서 [percentile] 위치의 값을 고른다.
     *
     * 왜 nearest-rank: 보간 없이 실제 표본값 하나를 그대로 돌려줘 "관측된 소요시간"이라는 의미가 유지된다.
     * 순위 계산은 `rank = ceil(percentile/100 * n)`(1-based)이고, 표본이 비면 0을 준다(빈 창 안전).
     *
     * [sortedAscending] 은 **호출부가 오름차순으로 정렬**해 넘겨야 한다(이 함수는 재정렬하지 않는다).
     */
    fun nearestRank(sortedAscending: List<Long>, percentile: Int): Long {
        if (sortedAscending.isEmpty()) {
            return 0L
        }
        val rank = ceil(percentile / 100.0 * sortedAscending.size).toInt()
        val index = rank.coerceIn(1, sortedAscending.size) - 1
        return sortedAscending[index]
    }
}
