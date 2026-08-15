package io.soo.bueongi.core.domain.admin.traffic

data class EndpointTraffic(
    val method: String,
    val endpoint: String,
    val count: Int,
    val successRate: Double,
    val avgDurationMs: Long,
    val p95DurationMs: Long,
    val distinctClients: Int,
)
