package io.soo.bueongi.core.admin.safetyspot.api.response

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint

data class RegionSafetySpotItemResponse(
    val type: SafetySpotType,
    val lat: Double,
    val lng: Double,
    val name: String,
) {
    companion object {
        fun from(point: SafetySpotPoint): RegionSafetySpotItemResponse =
            RegionSafetySpotItemResponse(
                type = point.type,
                lat = point.lat,
                lng = point.lng,
                name = point.name.orEmpty(),
            )
    }
}
