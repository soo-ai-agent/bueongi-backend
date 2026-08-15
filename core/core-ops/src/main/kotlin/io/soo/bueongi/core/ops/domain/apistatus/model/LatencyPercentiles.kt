package io.soo.bueongi.core.ops.domain.apistatus.model

data class LatencyPercentiles(
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
)
