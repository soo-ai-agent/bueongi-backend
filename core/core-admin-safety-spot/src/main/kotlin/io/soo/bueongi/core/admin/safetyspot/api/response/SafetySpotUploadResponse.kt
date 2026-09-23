package io.soo.bueongi.core.admin.safetyspot.api.response

import io.soo.bueongi.core.enums.SafetySpotCoordSystem
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotUploadResult

data class SafetySpotUploadResponse(
    val type: SafetySpotType,
    val coordSystem: SafetySpotCoordSystem,
    val received: Int,
    val saved: Int,
    val skipped: Int,
) {
    companion object {
        fun from(result: SafetySpotUploadResult): SafetySpotUploadResponse =
            SafetySpotUploadResponse(
                type = result.type,
                coordSystem = result.coordSystem,
                received = result.received,
                saved = result.saved,
                skipped = result.skipped,
            )
    }
}
