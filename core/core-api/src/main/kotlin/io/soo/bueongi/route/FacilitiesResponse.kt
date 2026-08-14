package io.soo.bueongi.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

data class FacilitiesResponse(
    val pois: List<FacilityPoi>,
    val summary: FacilitySummary,
)
