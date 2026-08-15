package io.soo.bueongi.core.facility.domain.status.model

import io.soo.bueongi.core.enums.FacilityRefreshStatus

import io.soo.bueongi.core.enums.FacilityType

data class FacilityStatus(
    val type: FacilityType,
    val loadedCount: Int,
    val lastRefreshAtMs: Long?,
    val lastRefreshCount: Int?,
    val lastRefreshStatus: FacilityRefreshStatus,
    val cdnGeneratedAt: String?,
    val cdnVersion: String?,
)
