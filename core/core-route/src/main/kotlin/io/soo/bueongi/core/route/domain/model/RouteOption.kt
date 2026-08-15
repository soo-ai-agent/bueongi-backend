package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.core.route.marker.model.FacilityPoi
import io.soo.bueongi.core.route.safety.model.SafetyBreakdown
import io.soo.bueongi.core.enums.RouteType

import com.fasterxml.jackson.annotation.JsonInclude
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.facility.model.GeoPoint

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RouteOption(
    val id: String,
    val name: String,
    val time: String,
    val dist: String,
    val desc: String,
    val tags: List<RouteTag>,
    val type: RouteType,
    val path: List<GeoPoint>? = null,
    val steps: List<NavStep>? = null,
    val score: Int? = null,
    val markers: List<FacilityPoi>? = null,
    val breakdown: SafetyBreakdown? = null,
    val provenance: DataProvenance? = null,
)
