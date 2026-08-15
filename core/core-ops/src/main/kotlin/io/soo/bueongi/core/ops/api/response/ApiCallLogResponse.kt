package io.soo.bueongi.core.ops.api.response

import io.soo.bueongi.storage.db.core.apilog.ApiCallLog

data class ApiCallLogResponse(
    val atEpochMs: Long,
    val provider: String,
    val endpoint: String,
    val success: Boolean,
    val durationMs: Long,
    val detail: String?,
) {
    companion object {

        fun from(log: ApiCallLog): ApiCallLogResponse = ApiCallLogResponse(
            atEpochMs = log.atEpochMs,
            provider = log.provider,
            endpoint = log.endpoint,
            success = log.success,
            durationMs = log.durationMs,
            detail = log.detail,
        )
    }
}
