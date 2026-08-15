package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.facility.model.GeoPoint

data class RouteCandidate(
    val searchOption: String,
    val path: List<GeoPoint>,
    val distanceM: Double,
    val timeS: Int,
    val steps: List<NavStep>,
)
