package io.soo.bueongi.core.share.domain.route.model

import com.fasterxml.jackson.annotation.JsonProperty

/** 보호자 화면은 경로가 저장되기 전에도 열린다. */
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
