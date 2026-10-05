package io.soo.bueongi.core.common.event

import io.soo.bueongi.core.enums.SafetySpotType

data class SafetySpotFetchPerformed(
    val type: SafetySpotType,
    val scope: String,
    // 호출이 실패하면 null, 성공하면 받은 행 수.
    val receivedCount: Int?,
)
