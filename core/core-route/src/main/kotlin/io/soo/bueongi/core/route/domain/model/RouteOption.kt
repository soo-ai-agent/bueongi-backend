package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.safety.model.SafetyBreakdown
import io.soo.bueongi.core.enums.RouteType

import com.fasterxml.jackson.annotation.JsonInclude
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

/** 폴백 경로는 점수·좌표·마커가 없다 — NON_NULL 이라 응답에서 빠진다. */
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
    val markers: List<SafetySpotPoi>? = null,
    val breakdown: SafetyBreakdown? = null,
    val provenance: DataProvenance? = null,
)
