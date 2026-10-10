package io.soo.bueongi.core.admin.ops.api.response

import io.soo.bueongi.core.admin.ops.domain.externalfetch.SafetySpotFetchStatus
import io.soo.bueongi.core.enums.SafetySpotType

data class SafetySpotFetchStatusResponse(
    val type: SafetySpotType,
    val successCount: Long,
    val failureCount: Long,
    val lastAtMs: Long,
    val lastScope: String,
    val lastReceivedCount: Int?,
) {
    companion object {
        fun from(status: SafetySpotFetchStatus): SafetySpotFetchStatusResponse {
            return SafetySpotFetchStatusResponse(
                type = status.type,
                successCount = status.successCount,
                failureCount = status.failureCount,
                lastAtMs = status.lastAtMs,
                lastScope = status.lastScope,
                lastReceivedCount = status.lastReceivedCount,
            )
        }

        fun from(statuses: List<SafetySpotFetchStatus>): List<SafetySpotFetchStatusResponse> {
            val responses: ArrayList<SafetySpotFetchStatusResponse> = ArrayList(statuses.size)
            for (status in statuses) {
                responses.add(from(status))
            }
            return responses
        }
    }
}
