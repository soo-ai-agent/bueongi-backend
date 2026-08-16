package io.soo.bueongi.core.share.domain.route.model

import com.fasterxml.jackson.annotation.JsonProperty

data class RouteContext(
    val destination: RouteDestination,
    val path: List<RoutePoint>,
    val markers: List<RouteMarker>,
    @param:JsonProperty("total_distance_m")
    // 프론트가 보내지 않으면 null 이 가능함
    val totalDistanceM: Int?,
    @param:JsonProperty("total_time_s")
    // 프론트가 보내지 않으면 null 이 가능함
    val totalTimeS: Int?,
    // 경로 종류를 고르지 않으면 null 이 가능함
    val type: String? = null,
    // 데이터 출처를 모르면 null 이 가능함
    val provenance: String? = null,
)
