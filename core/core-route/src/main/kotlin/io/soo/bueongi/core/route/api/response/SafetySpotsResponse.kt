package io.soo.bueongi.core.route.api.response

import io.soo.bueongi.core.route.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.model.RouteSafetySpots
import io.soo.bueongi.core.route.domain.model.SafetySpotSummary

data class SafetySpotsResponse(
    val pois: List<SafetySpotPoi>,
    val summary: SafetySpotSummary,
) {
    companion object {

        fun from(safetySpots: RouteSafetySpots): SafetySpotsResponse =
            SafetySpotsResponse(pois = safetySpots.pois, summary = safetySpots.summary)
    }
}
