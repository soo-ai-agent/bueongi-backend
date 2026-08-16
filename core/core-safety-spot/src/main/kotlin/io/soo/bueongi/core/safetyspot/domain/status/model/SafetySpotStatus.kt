package io.soo.bueongi.core.safetyspot.domain.status.model

import io.soo.bueongi.core.enums.SafetySpotRefreshStatus

import io.soo.bueongi.core.enums.SafetySpotType

data class SafetySpotStatus(
    val type: SafetySpotType,
    val loadedCount: Int,
    // 한 번도 갱신하지 않았으면 null 이 가능함
    val lastRefreshAtMs: Long?,
    // 갱신이 실패하면 null 이 가능함
    val lastRefreshCount: Int?,
    val lastRefreshStatus: SafetySpotRefreshStatus,
    // manifest 가 없는 원천은 null 이 가능함
    val cdnGeneratedAt: String?,
    // manifest 가 없는 원천은 null 이 가능함
    val cdnVersion: String?,
)
