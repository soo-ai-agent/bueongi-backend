package io.soo.bueongi.core.safetyspot.domain.csv.model

import io.soo.bueongi.core.enums.SafetySpotCoordSystem
import io.soo.bueongi.core.enums.SafetySpotType

data class SafetySpotUploadResult(
    val type: SafetySpotType,
    val coordSystem: SafetySpotCoordSystem,
    val received: Int,
    val saved: Int,
    val skipped: Int,
)
