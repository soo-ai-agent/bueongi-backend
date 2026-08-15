package io.soo.bueongi.core.api.controller.v1.response

import io.soo.bueongi.core.domain.route.FacilityPoi
import io.soo.bueongi.core.domain.route.RouteFacilities
import io.soo.bueongi.core.domain.route.FacilitySummary

data class FacilitiesResponse(
    val pois: List<FacilityPoi>,
    val summary: FacilitySummary,
) {
    companion object {

        fun from(facilities: RouteFacilities): FacilitiesResponse =
            FacilitiesResponse(pois = facilities.pois, summary = facilities.summary)
    }
}
