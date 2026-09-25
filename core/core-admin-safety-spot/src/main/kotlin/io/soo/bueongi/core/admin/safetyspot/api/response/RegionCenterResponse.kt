package io.soo.bueongi.core.admin.safetyspot.api.response

import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

data class RegionCenterResponse(val lat: Double, val lng: Double) {
    companion object {
        fun from(center: GeoPoint): RegionCenterResponse = RegionCenterResponse(lat = center.lat, lng = center.lng)
    }
}
