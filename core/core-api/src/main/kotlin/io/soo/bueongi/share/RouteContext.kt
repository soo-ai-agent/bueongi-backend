package io.soo.bueongi.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

data class RouteContext(
    val destination: RouteDestination,
    val path: List<RoutePoint>,
    val markers: List<RouteMarker>,
    @JsonProperty("total_distance_m")
    val totalDistanceM: Int?,
    @JsonProperty("total_time_s")
    val totalTimeS: Int?,
    val type: String? = null,
    val provenance: String? = null,
)
