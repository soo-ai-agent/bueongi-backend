package io.soo.bueongi.storage.db.core.facility.dto

import io.soo.bueongi.storage.db.core.facility.model.GeoPoint
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CoordDto(
    val lat: Double? = null,
    val lng: Double? = null,
) {
    fun toGeoPoint(): GeoPoint? = GeoPoint.orNull(lat, lng)
}
