package io.soo.bueongi.core.admin.domain.apistatus

data class LatencyPercentiles(
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
)
