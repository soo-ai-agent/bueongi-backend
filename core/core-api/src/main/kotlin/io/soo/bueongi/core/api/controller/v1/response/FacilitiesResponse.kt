package io.soo.bueongi.core.api.controller.v1.response

import io.soo.bueongi.core.domain.route.FacilityPoi
import io.soo.bueongi.core.domain.route.FacilitySummary

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

data class FacilitiesResponse(
    val pois: List<FacilityPoi>,
    val summary: FacilitySummary,
)
