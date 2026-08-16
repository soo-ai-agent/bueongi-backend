package io.soo.bueongi.core.safetyspot.domain.status.model

import io.soo.bueongi.core.enums.SafetySpotRefreshStatus

import io.soo.bueongi.core.enums.SafetySpotType

/** 한 번도 갱신 안 한 종류는 갱신 정보가 없다. */
data class SafetySpotStatus(
    val type: SafetySpotType,
    val loadedCount: Int,
    val lastRefreshAtMs: Long?,
    val lastRefreshCount: Int?,
    val lastRefreshStatus: SafetySpotRefreshStatus,
    val cdnGeneratedAt: String?,
    val cdnVersion: String?,
)
