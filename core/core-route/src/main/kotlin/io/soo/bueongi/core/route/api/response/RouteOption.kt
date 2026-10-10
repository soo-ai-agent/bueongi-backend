package io.soo.bueongi.core.route.api.response

import com.fasterxml.jackson.annotation.JsonInclude
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.model.NavigationStep
import io.soo.bueongi.core.route.domain.model.RouteTag
import io.soo.bueongi.core.route.domain.safety.model.SafetyBreakdown
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
    val path: List<GeoPoint>? = null,
    val steps: List<NavigationStep>? = null,
    val score: Int? = null,
    val markers: List<SafetySpotPoi>? = null,
    val breakdown: SafetyBreakdown? = null,
    val provenance: DataProvenance? = null,
)
