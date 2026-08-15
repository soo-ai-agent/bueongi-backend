package io.soo.bueongi.core.ops.domain.traffic

data class DailyActive(
    val dateEpochMs: Long,
    val distinctClients: Int,
)
