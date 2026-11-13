package io.soo.bueongi.core.safetyspot.api.response

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

data class NearbySafetySpotsResponse(
    val counts: List<NearbySafetySpotCountResponse>,
    val spots: List<NearbySafetySpotResponse>,
) {
    companion object {
        fun from(
            countsByType: Map<SafetySpotType, Int>,
            spotsByType: Map<SafetySpotType, List<SafetySpotPoint>>,
        ): NearbySafetySpotsResponse {
            val counts: ArrayList<NearbySafetySpotCountResponse> = ArrayList(countsByType.size)
            val spots: ArrayList<NearbySafetySpotResponse> = ArrayList()
            for ((type, count) in countsByType) {
                val wire: String? = type.markerWire
                // 지도 마커로 안 내보내는 종류(범죄주의구간)는 이 응답에도 싣지 않는다.
                if (wire == null) {
                    continue
                }
                counts.add(NearbySafetySpotCountResponse(type = wire, count = count))
                for (point in spotsByType[type].orEmpty()) {
                    spots.add(
                        NearbySafetySpotResponse(
                            type = wire,
                            lat = point.lat,
                            lng = point.lng,
                            // 경로 마커 직렬화와 같은 규칙 — 결측은 빈 값으로 메워 보낸다.
                            name = point.name ?: "",
                            address = point.address ?: "",
                            phone = point.phoneOrNull() ?: "",
                        ),
                    )
                }
            }
            return NearbySafetySpotsResponse(counts = counts, spots = spots)
        }
    }
}

data class NearbySafetySpotCountResponse(
    val type: String,
    val count: Int,
)

data class NearbySafetySpotResponse(
    val type: String,
    val lat: Double,
    val lng: Double,
    val name: String,
    val address: String,
    val phone: String,
)
