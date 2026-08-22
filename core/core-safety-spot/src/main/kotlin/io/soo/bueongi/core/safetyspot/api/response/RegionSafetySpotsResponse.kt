package io.soo.bueongi.core.safetyspot.api.response

import io.soo.bueongi.core.safetyspot.domain.status.model.RegionSafetySpots
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint

/**
 * 자치단체 한 곳의 시설 지점 목록.
 *
 * 자른 것을 숨기지 않는다 — [total] 은 전체, [returned] 는 실제로 보낸 개수라
 * 화면이 "5,231개 중 2,000개 표시"라고 말할 수 있다.
 */
data class RegionSafetySpotsResponse(
    val code: String,
    val name: String,
    val sido: String,
    val total: Int,
    val returned: Int,
    val spots: List<RegionSafetySpotItemResponse>,
) {
    companion object {
        fun from(found: RegionSafetySpots): RegionSafetySpotsResponse {
            val spots: ArrayList<RegionSafetySpotItemResponse> = ArrayList(found.spots.size)
            for (point: SafetySpotPoint in found.spots) {
                spots.add(RegionSafetySpotItemResponse.from(point))
            }
            return RegionSafetySpotsResponse(
                code = found.code,
                name = found.name,
                sido = found.sido,
                total = found.total,
                returned = spots.size,
                spots = spots,
            )
        }
    }
}
