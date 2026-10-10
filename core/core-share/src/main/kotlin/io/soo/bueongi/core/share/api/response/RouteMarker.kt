package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.model.StoredMarker

data class RouteMarker(
    val type: String,
    val lat: Double,
    val lng: Double,
    val x: Double?,
    val y: Double?,
    val name: String?,
    val purpose: String?,
    @param:JsonProperty("camera_count")
    val cameraCount: Int?,
    val address: String?,
    val phone: String?,
) {
    companion object {
        fun from(marker: StoredMarker): RouteMarker {
            return RouteMarker(
                type = marker.type,
                lat = marker.lat,
                lng = marker.lng,
                x = marker.x,
                y = marker.y,
                name = marker.name,
                purpose = marker.purpose,
                cameraCount = marker.cameraCount,
                address = marker.address,
                phone = marker.phone,
            )
        }

        fun from(markers: List<StoredMarker>): List<RouteMarker> {
            val routeMarkers: ArrayList<RouteMarker> = ArrayList(markers.size)
            for (marker in markers) {
                routeMarkers.add(from(marker))
            }
            return routeMarkers
        }
    }
}
