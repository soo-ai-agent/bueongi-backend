package io.soo.bueongi.core.admin.ops.domain.external.model

data class ExternalApiEndpoint(
    val endpoint: String,
    val count: Int,
    val failureCount: Int,
    val avgDurationMs: Long,
)
