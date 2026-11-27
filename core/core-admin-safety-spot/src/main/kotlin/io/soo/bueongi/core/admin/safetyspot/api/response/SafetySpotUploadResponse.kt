package io.soo.bueongi.core.admin.safetyspot.api.response

import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCoordSystem
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCsvReadResult
import io.soo.bueongi.core.enums.SafetySpotType

data class SafetySpotUploadResponse(
    val type: SafetySpotType,
    val coordSystem: SafetySpotCoordSystem,
    val received: Int,
    val saved: Int,
    val skipped: Int,
) {
    companion object {
        fun from(type: SafetySpotType, result: SafetySpotCsvReadResult, saved: Int): SafetySpotUploadResponse {
            return SafetySpotUploadResponse(
                type = type,
                coordSystem = result.coordSystem,
                received = result.received,
                saved = saved,
                skipped = result.skipped,
            )
        }
    }
}
