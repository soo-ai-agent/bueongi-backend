package io.soo.bueongi.storage.db.safetyspot.model
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.cctv.CctvSpot

@JsonIgnoreProperties(ignoreUnknown = true)
data class StoredSafetySpotItem(
    val name: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val sigunguCode: String? = null,
    val coords: List<StoredSafetySpotCoord>? = null,
    val address: String? = null,
    val phone: String? = null,
    val properties: StoredSafetySpotProperties? = null,
) {
    fun toSafetySpotPoint(type: SafetySpotType): SafetySpotPoint? {
        val point: GeoPoint = GeoPoint.orNull(lat, lng) ?: return null
        val trimmedAddress: String = address.orEmpty().trim()
        val trimmedPhone: String = phone.orEmpty().trim()
        return SafetySpotPoint.of(
            type = type,
            lat = point.lat,
            lng = point.lng,
            name = name,
            regionCode = sigunguCode,
            purpose = properties?.purpose,
            cameraCount = properties?.cameraCount,
            address = trimmedAddress.ifEmpty { null },
            phone = trimmedPhone.ifEmpty { null },
        )
    }

    fun toSafePath(): SafePath? {
        val coordList: List<GeoPoint> = coords.orEmpty().mapNotNull { it.toGeoPoint() }
        if (coordList.isNotEmpty()) {
            return SafePath(coords = coordList)
        }
        val point: GeoPoint = GeoPoint.orNull(lat, lng) ?: return null
        return SafePath(coords = listOf(point))
    }
}

private fun SafetySpotPoint.cctvPropertiesOrNull(): StoredSafetySpotProperties? {
    if (this !is CctvSpot) {
        return null
    }
    if (purpose == null && cameraCount == null) {
        return null
    }
    return StoredSafetySpotProperties(purpose = purpose, cameraCount = cameraCount)
}

internal fun SafetySpotPoint.toStoredItem(): StoredSafetySpotItem {
    return StoredSafetySpotItem(
        name = name,
        lat = lat,
        lng = lng,
        sigunguCode = regionCodeOrNull(),
        address = address,
        phone = phoneOrNull(),
        properties = cctvPropertiesOrNull(),
    )
}
