package io.soo.bueongi.storage.db.core.facility

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CoordDto(
    val lat: Double? = null,
    val lng: Double? = null,
) {
    fun toGeoPoint(): GeoPoint? = GeoPoint.orNull(lat, lng)
}
