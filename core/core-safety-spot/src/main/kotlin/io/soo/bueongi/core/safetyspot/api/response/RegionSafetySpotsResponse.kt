package io.soo.bueongi.core.safetyspot.api.response

import io.soo.bueongi.core.safetyspot.domain.status.model.RegionSafetySpots
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint

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
