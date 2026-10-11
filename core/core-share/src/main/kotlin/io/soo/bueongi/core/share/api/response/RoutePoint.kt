package io.soo.bueongi.core.share.api.response

import io.soo.bueongi.storage.db.share.model.StoredCoord

data class RoutePoint(
    val lat: Double,
    val lng: Double,
) {
    companion object {
        fun from(coord: StoredCoord): RoutePoint {
            return RoutePoint(lat = coord.lat, lng = coord.lng)
        }

        fun from(coords: List<StoredCoord>): List<RoutePoint> {
            val points: ArrayList<RoutePoint> = ArrayList(coords.size)
            for (coord in coords) {
                points.add(from(coord))
            }
            return points
        }
    }
}
