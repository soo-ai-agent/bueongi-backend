package io.soo.bueongi.core.safetyspot.api.response

import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

/** 지도에서 이 지역을 가리킬 한 점. */
data class RegionCenterResponse(
    val lat: Double,
    val lng: Double,
) {
    companion object {
        fun from(center: GeoPoint): RegionCenterResponse =
            RegionCenterResponse(lat = center.lat, lng = center.lng)
    }
}
