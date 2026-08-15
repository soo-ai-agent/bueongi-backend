package io.soo.bueongi.storage.db.core.facility
import io.soo.bueongi.core.enums.FacilityType

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class FacilityItemDto(
    val name: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val sigunguCode: String? = null,
    val coords: List<CoordDto>? = null,
    val address: String? = null,
    val phone: String? = null,
    val properties: FacilityItemPropertiesDto? = null,
) {
    fun toFacilityPoint(type: FacilityType): FacilityPoint? {
        val point: GeoPoint = GeoPoint.orNull(lat, lng) ?: return null
        return FacilityPoint(
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
