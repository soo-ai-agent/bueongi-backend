package io.soo.bueongi.core.admin.safetyspot.api.response

import io.soo.bueongi.core.admin.safetyspot.domain.status.model.RegionSafetySpotCount
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint

data class RegionSafetySpotResponse(
    val code: String,
    val sido: String,
    val name: String,
    val counts: Map<String, Int>,
    val total: Int,
    val fileCount: Int,
    val apiCount: Int,
    val center: RegionCenterResponse?,
) {
    companion object {
        fun from(count: RegionSafetySpotCount): RegionSafetySpotResponse {
            val counts: LinkedHashMap<String, Int> = LinkedHashMap()
            for ((type, typeCount) in count.countsByType) {
                counts[type.cdnType] = typeCount
            }
            val center: GeoPoint? = count.center
            var centerResponse: RegionCenterResponse? = null
            if (center != null) {
                centerResponse = RegionCenterResponse.from(center)
            }
            return RegionSafetySpotResponse(
                code = count.code,
                sido = count.sido,
                name = count.name,
                counts = counts,
                total = count.total,
                fileCount = count.fileCount,
                apiCount = count.apiCount,
                center = centerResponse,
            )
        }

        fun from(counts: List<RegionSafetySpotCount>): List<RegionSafetySpotResponse> {
            val responses: ArrayList<RegionSafetySpotResponse> = ArrayList(counts.size)
            for (count in counts) {
                responses.add(from(count))
            }
            return responses
        }
    }
}
