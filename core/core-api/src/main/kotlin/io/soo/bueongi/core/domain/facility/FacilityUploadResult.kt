package io.soo.bueongi.core.domain.facility

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityLiveStore
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilityUploadStore
import io.soo.bueongi.core.support.error.EmptyFacilityUploadException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class FacilityUploadResult(
    val type: FacilityType,
    val coordSystem: FacilityCoordSystem,
    val received: Int,
    val saved: Int,
    val skipped: Int,
)
