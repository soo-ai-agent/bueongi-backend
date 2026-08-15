package io.soo.bueongi.core.ops.domain.traffic.model

data class LatencyPercentiles(
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
)
