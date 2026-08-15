package io.soo.bueongi.core.api.controller.v1.response

data class ApiCallLogResponse(
    val atEpochMs: Long,
    val provider: String,
    val endpoint: String,
    val success: Boolean,
    val durationMs: Long,
    val detail: String?,
)
