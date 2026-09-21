package io.soo.bueongi.core.route.stats.domain.model

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference

data class RouteChoice(
    val chosenType: RouteType,
    val candidateCount: Int,
    val score: Int?,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val preference: SafetyPreference,
    val destinationName: String?,
)
