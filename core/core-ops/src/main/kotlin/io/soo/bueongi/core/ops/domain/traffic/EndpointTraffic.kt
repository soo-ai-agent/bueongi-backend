package io.soo.bueongi.core.ops.domain.traffic

data class EndpointTraffic(
    val method: String,
    val endpoint: String,
    val count: Int,
    val successRate: Double,
    val avgDurationMs: Long,
    val p95DurationMs: Long,
    val distinctClients: Int,
)
