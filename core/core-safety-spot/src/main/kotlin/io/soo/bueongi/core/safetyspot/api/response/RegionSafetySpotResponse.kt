package io.soo.bueongi.core.safetyspot.api.response

import io.soo.bueongi.core.safetyspot.domain.status.model.RegionSafetySpotCount
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

data class RegionSafetySpotResponse(
    val code: String,
    val sido: String,
    val name: String,
    val counts: Map<String, Int>,
    val total: Int,
    val center: RegionCenterResponse?,
) {
    companion object {
        fun from(count: RegionSafetySpotCount): RegionSafetySpotResponse {
            val counts: LinkedHashMap<String, Int> = LinkedHashMap()
            for ((type, n) in count.countsByType) {
                counts[type.cdnType] = n
            }
            val center: GeoPoint? = count.center
            return RegionSafetySpotResponse(
                code = count.code,
                sido = count.sido,
                name = count.name,
                counts = counts,
                total = count.total,
                center =
                    if (center == null) {
                        null
                    } else {
                        RegionCenterResponse.from(center)
                    },
            )
        }
    }
}
