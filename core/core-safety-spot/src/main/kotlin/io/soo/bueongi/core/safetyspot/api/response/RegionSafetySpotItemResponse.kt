package io.soo.bueongi.core.safetyspot.api.response

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint

/** 지도에 찍을 시설 한 점. [type] 은 enum 의 와이어 값(cctv·lamp…)으로 나간다. */
data class RegionSafetySpotItemResponse(
    val type: SafetySpotType,
    val lat: Double,
    val lng: Double,
    // 원천이 이름을 주지 않는 시설(가로등·보안등 등)은 null 이 가능함
    val name: String?,
) {
    companion object {
        fun from(point: SafetySpotPoint): RegionSafetySpotItemResponse =
            RegionSafetySpotItemResponse(
                type = point.type,
                lat = point.lat,
                lng = point.lng,
                name = point.name,
            )
    }
}
