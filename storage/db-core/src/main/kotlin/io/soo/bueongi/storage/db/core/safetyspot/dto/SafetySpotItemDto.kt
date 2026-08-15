package io.soo.bueongi.storage.db.core.safetyspot.dto
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import io.soo.bueongi.core.enums.SafetySpotType

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SafetySpotItemDto(
    val name: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val sigunguCode: String? = null,
    val coords: List<CoordDto>? = null,
    val address: String? = null,
    val phone: String? = null,
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
