package io.soo.bueongi.core.admin.safetyspot.api.response

import io.soo.bueongi.core.admin.safetyspot.domain.status.model.SafetySpotStatus

data class SafetySpotStatusResponse(
    val safetySpots: List<SafetySpotStatus>,
) {
    companion object {
        fun from(safetySpots: List<SafetySpotStatus>): SafetySpotStatusResponse {
            return SafetySpotStatusResponse(safetySpots = safetySpots)
        }
    }
}
