package io.soo.bueongi.core.safetyspot.api.request

import io.soo.bueongi.core.safetyspot.domain.nearby.error.CoordinateOutOfRangeException
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint

data class NearbySafetySpotsRequest(
    private val lat: Double,
    private val lng: Double,
) {
    val center: GeoPoint = requireInRange(lat, lng)

    private fun requireInRange(lat: Double, lng: Double): GeoPoint {
        if (!GeoPoint.isValidLat(lat)) {
            throw CoordinateOutOfRangeException("lat", lat)
        }
        if (!GeoPoint.isValidLng(lng)) {
            throw CoordinateOutOfRangeException("lng", lng)
        }
        return GeoPoint(lat, lng)
    }
}
