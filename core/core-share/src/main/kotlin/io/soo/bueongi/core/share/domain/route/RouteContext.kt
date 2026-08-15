package io.soo.bueongi.core.share.domain.route

import com.fasterxml.jackson.annotation.JsonProperty

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
