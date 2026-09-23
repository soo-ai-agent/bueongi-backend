package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.SafetyPreference

data class RouteRecommendationLog(
    val atEpochMs: Long,
    val endpoint: String,
    val destinationName: String?,
    val preference: SafetyPreference,
)
