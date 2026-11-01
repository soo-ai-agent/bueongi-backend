package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.share.model.StoredRoute

data class RouteContext(
    val destination: RouteDestination,
    val path: List<RoutePoint>,
    val markers: List<RouteMarker>,
    @param:JsonProperty("total_distance_m")
    val totalDistanceMeters: Int,
    @param:JsonProperty("total_time_s")
    val totalTimeSeconds: Int,
    val type: String,
) {
    companion object {
        fun from(route: StoredRoute): RouteContext {
            return RouteContext(
                destination = RouteDestination.from(route.destination),
                path = RoutePoint.from(route.path),
                markers = RouteMarker.from(route.markers),
                totalDistanceMeters = route.totalDistanceMeters,
                totalTimeSeconds = route.totalTimeSeconds,
                type = route.type,
            )
        }
    }
}
