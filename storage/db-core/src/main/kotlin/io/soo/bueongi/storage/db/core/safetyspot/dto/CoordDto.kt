package io.soo.bueongi.storage.db.core.safetyspot.dto

import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CoordDto(
    // 원천 좌표가 깨져 있으면 null 이 가능함
    val lat: Double? = null,
    // 원천 좌표가 깨져 있으면 null 이 가능함
    val lng: Double? = null,
) {
    fun toGeoPoint(): GeoPoint? =
        GeoPoint.orNull(lat, lng)
}
