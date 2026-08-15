package io.soo.bueongi.core.facility.domain.csv.model

import io.soo.bueongi.core.enums.FacilityCoordSystem

import io.soo.bueongi.core.enums.FacilityType

data class FacilityUploadResult(
    val type: FacilityType,
    val coordSystem: FacilityCoordSystem,
    val received: Int,
    val saved: Int,
    val skipped: Int,
)
