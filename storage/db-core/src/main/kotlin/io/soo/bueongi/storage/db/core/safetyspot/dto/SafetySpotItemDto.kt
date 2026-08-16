package io.soo.bueongi.storage.db.core.safetyspot.dto
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import io.soo.bueongi.core.enums.SafetySpotType

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SafetySpotItemDto(
    // 원천에 값이 없으면 null 이 가능함
    val name: String? = null,
    // 원천 행에 좌표가 없으면 null 이 가능함
    val lat: Double? = null,
    // 원천 행에 좌표가 없으면 null 이 가능함
    val lng: Double? = null,
    // 원천에 값이 없으면 null 이 가능함
    val sigunguCode: String? = null,
    // 점 하나짜리 원천은 null 이 가능함
    val coords: List<CoordDto>? = null,
    // 지구대가 아닌 시설은 null 이 가능함
    val address: String? = null,
    // 지구대가 아닌 시설은 null 이 가능함
    val phone: String? = null,
    // 부가 정보를 주지 않는 원천은 null 이 가능함
    val properties: SafetySpotItemPropertiesDto? = null,
) {
    fun toSafetySpotPoint(type: SafetySpotType): SafetySpotPoint? {
        val point: GeoPoint = GeoPoint.orNull(lat, lng) ?: return null
        return SafetySpotPoint(
            type = type,
            lat = point.lat,
            lng = point.lng,
            name = name,
            sigungu = sigunguCode,
            purpose = properties?.purpose,
            cameraCount = properties?.cameraCount,
            address = address?.trim()?.takeIf { it.isNotEmpty() },
            phone = phone?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun toSafePath(): SafePath? {
        val coordList: List<GeoPoint>? = coords?.mapNotNull { it.toGeoPoint() }
        if (!coordList.isNullOrEmpty()) {
            return SafePath(coords = coordList, name = name)
        }
        val point: GeoPoint = GeoPoint.orNull(lat, lng) ?: return null
        return SafePath(coords = listOf(point), name = name)
    }
}
