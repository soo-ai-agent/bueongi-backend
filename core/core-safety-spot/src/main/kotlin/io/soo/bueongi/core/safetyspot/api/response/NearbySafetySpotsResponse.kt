package io.soo.bueongi.core.safetyspot.api.response

import io.soo.bueongi.core.enums.SafetySpotType

data class NearbySafetySpotsResponse(
    val counts: List<NearbySafetySpotCountResponse>,
) {
    companion object {
        fun from(countsByType: Map<SafetySpotType, Int>): NearbySafetySpotsResponse {
            val counts: ArrayList<NearbySafetySpotCountResponse> = ArrayList(countsByType.size)
            for ((type, count) in countsByType) {
                val wire: String? = type.markerWire
                // 지도 마커로 안 내보내는 종류(범죄주의구간)는 이 응답에도 싣지 않는다.
                if (wire == null) {
                    continue
                }
                counts.add(NearbySafetySpotCountResponse(type = wire, count = count))
            }
            return NearbySafetySpotsResponse(counts = counts)
        }
    }
}

data class NearbySafetySpotCountResponse(
    val type: String,
    val count: Int,
)
