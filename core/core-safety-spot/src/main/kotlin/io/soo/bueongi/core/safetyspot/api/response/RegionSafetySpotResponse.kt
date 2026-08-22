package io.soo.bueongi.core.safetyspot.api.response

import io.soo.bueongi.core.safetyspot.domain.status.model.RegionSafetySpotCount
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

/** 자치단체 한 곳의 종류별 적재 건수. 종류 키는 와이어 값(cctv·lamp…)을 그대로 쓴다. */
data class RegionSafetySpotResponse(
    val code: String,
    val sido: String,
    val name: String,
    val counts: Map<String, Int>,
    val total: Int,
    // 쓸 수 있는 좌표가 한 점도 없는 지역은 null 이 가능함
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
                center = if (center == null) {
                    null
                } else {
                    RegionCenterResponse.from(center)
                },
            )
        }
    }
}
