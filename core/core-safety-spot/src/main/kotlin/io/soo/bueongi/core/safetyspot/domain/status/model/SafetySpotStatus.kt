package io.soo.bueongi.core.safetyspot.domain.status.model

import io.soo.bueongi.core.enums.SafetySpotRefreshStatus
import io.soo.bueongi.core.enums.SafetySpotType

data class SafetySpotStatus(
    val type: SafetySpotType,
    val loadedCount: Int,
    val lastRefreshAtMs: Long?,
    val lastRefreshCount: Int?,
    val lastRefreshStatus: SafetySpotRefreshStatus,
    val cdnGeneratedAt: String?,
    val cdnVersion: String?,
)
