package io.soo.bueongi.storage.db.safetyspot.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class StoredSafetySpotCoord(
    val lat: Double? = null,
    val lng: Double? = null,
) {
    fun toGeoPoint(): GeoPoint? {
        return GeoPoint.orNull(lat, lng)
    }
}
