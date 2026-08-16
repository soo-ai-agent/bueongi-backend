package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.safety.model.SafetyBreakdown
import io.soo.bueongi.core.enums.RouteType

import com.fasterxml.jackson.annotation.JsonInclude
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RouteOption(
    val id: String,
    val name: String,
    val time: String,
    val dist: String,
    val desc: String,
    val tags: List<RouteTag>,
    val type: RouteType,
    // 폴백 경로는 null 이 가능함
    val path: List<GeoPoint>? = null,
    // 폴백 경로는 null 이 가능함
    val steps: List<NavStep>? = null,
    // 폴백 경로는 null 이 가능함
    val score: Int? = null,
    // 폴백 경로는 null 이 가능함
    val markers: List<SafetySpotPoi>? = null,
    // 폴백 경로는 null 이 가능함
    val breakdown: SafetyBreakdown? = null,
    // 폴백 경로는 null 이 가능함
    val provenance: DataProvenance? = null,
)
