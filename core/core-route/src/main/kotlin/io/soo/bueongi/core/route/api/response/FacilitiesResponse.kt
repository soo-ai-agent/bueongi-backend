package io.soo.bueongi.core.route.api.response

import io.soo.bueongi.core.route.marker.model.FacilityPoi
import io.soo.bueongi.core.route.domain.model.RouteFacilities
import io.soo.bueongi.core.route.domain.model.FacilitySummary

data class FacilitiesResponse(
    val pois: List<FacilityPoi>,
    val summary: FacilitySummary,
) {
    companion object {

        fun from(facilities: RouteFacilities): FacilitiesResponse =
            FacilitiesResponse(pois = facilities.pois, summary = facilities.summary)
    }
}
