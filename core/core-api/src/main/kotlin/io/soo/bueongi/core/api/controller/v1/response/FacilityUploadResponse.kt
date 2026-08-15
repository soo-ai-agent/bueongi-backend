package io.soo.bueongi.core.api.controller.v1.response

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.enums.FacilityCoordSystem
import io.soo.bueongi.core.domain.facility.csv.FacilityUploadResult

data class FacilityUploadResponse(
    val type: FacilityType,
    val coordSystem: FacilityCoordSystem,
    val received: Int,
    val saved: Int,
    val skipped: Int,
) {
    companion object {
        fun from(result: FacilityUploadResult): FacilityUploadResponse = FacilityUploadResponse(
            type = result.type,
            coordSystem = result.coordSystem,
            received = result.received,
            saved = result.saved,
            skipped = result.skipped,
        )
    }
}
