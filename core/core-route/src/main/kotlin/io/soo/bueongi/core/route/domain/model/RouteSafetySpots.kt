package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi

data class RouteSafetySpots(
    val pois: List<SafetySpotPoi>,
    val summary: SafetySpotSummary,
)
