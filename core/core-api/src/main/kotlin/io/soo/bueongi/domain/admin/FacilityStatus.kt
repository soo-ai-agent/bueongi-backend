package io.soo.bueongi.domain.admin

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
