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
    val type: String? = null,
    val provenance: String? = null,
)

fun StoredRoute.toRouteContext(): RouteContext {
    return RouteContext(
        destination = RouteDestination(name = destination.name, lat = destination.lat, lng = destination.lng),
        path = path.map { RoutePoint(lat = it.lat, lng = it.lng) },
        markers =
            markers.map { marker ->
                RouteMarker(
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
            },
        totalDistanceM = totalDistanceM,
        totalTimeS = totalTimeS,
        type = type,
        provenance = provenance,
    )
}
