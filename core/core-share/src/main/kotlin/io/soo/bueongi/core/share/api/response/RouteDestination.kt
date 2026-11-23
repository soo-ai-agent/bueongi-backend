package io.soo.bueongi.core.share.api.response

import io.soo.bueongi.storage.db.share.model.StoredRouteDestination

data class RouteDestination(
    val name: String,
    val lat: Double,
    val lng: Double,
) {
    companion object {
        fun from(destination: StoredRouteDestination): RouteDestination {
            return RouteDestination(name = destination.name, lat = destination.lat, lng = destination.lng)
        }
    }
}
