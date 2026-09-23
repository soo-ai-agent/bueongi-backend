package io.soo.bueongi.core.admin.ops.domain.traffic.model

data class EndpointTraffic(
    val method: String,
    val endpoint: String,
    val count: Int,
    val successRate: Double,
    val avgDurationMs: Long,
    val p95DurationMs: Long,
    val distinctClients: Int,
)
