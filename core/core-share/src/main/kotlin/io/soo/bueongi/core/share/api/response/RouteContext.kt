package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.model.StoredRoute

data class RouteContext(
    val destination: RouteDestination,
    val path: List<RoutePoint>,
    val markers: List<RouteMarker>,
    @param:JsonProperty("total_distance_m")
    val totalDistanceM: Int?,
    @param:JsonProperty("total_time_s")
    val totalTimeS: Int?,
    val type: String?,
    val provenance: String?,
) {
    companion object {
        fun from(route: StoredRoute): RouteContext {
            return RouteContext(
                destination = RouteDestination.from(route.destination),
                path = RoutePoint.from(route.path),
                markers = RouteMarker.from(route.markers),
                totalDistanceM = route.totalDistanceM,
                totalTimeS = route.totalTimeS,
                type = route.type,
                provenance = route.provenance,
            )
        }
    }
}
