package io.soo.bueongi.core.safetyspot.api.response

import io.soo.bueongi.core.safetyspot.domain.status.model.RegionSafetySpotCount

/** 자치단체 한 곳의 종류별 적재 건수. 종류 키는 와이어 값(cctv·lamp…)을 그대로 쓴다. */
data class RegionSafetySpotResponse(
    val code: String,
    val sido: String,
    val name: String,
    val counts: Map<String, Int>,
    val total: Int,
) {
    companion object {
        fun from(count: RegionSafetySpotCount): RegionSafetySpotResponse {
            val counts: LinkedHashMap<String, Int> = LinkedHashMap()
            for ((type, n) in count.countsByType) {
                counts[type.cdnType] = n
            }
            return RegionSafetySpotResponse(
                code = count.code,
                sido = count.sido,
                name = count.name,
                counts = counts,
                total = count.total,
            )
        }
    }
}
