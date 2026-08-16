package io.soo.bueongi.storage.db.core.safetyspot.dto

import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** 원천마다 채우는 필드가 달라 전부 비어 있을 수 있다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CoordDto(
    val lat: Double? = null,
    val lng: Double? = null,
) {
    fun toGeoPoint(): GeoPoint? =
        GeoPoint.orNull(lat, lng)
}
