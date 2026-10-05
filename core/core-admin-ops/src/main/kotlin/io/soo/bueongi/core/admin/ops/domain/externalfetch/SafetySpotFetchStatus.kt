package io.soo.bueongi.core.admin.ops.domain.externalfetch

import io.soo.bueongi.core.enums.SafetySpotType

data class SafetySpotFetchStatus(
    val type: SafetySpotType,
    val successCount: Long,
    val failureCount: Long,
    val lastAtMs: Long,
    val lastScope: String,
    // 마지막 호출이 실패였으면 null.
    val lastReceivedCount: Int?,
)
