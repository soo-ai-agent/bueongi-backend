package io.soo.bueongi.core.admin.domain.traffic

data class DailyActive(
    val dateEpochMs: Long,
    val distinctClients: Int,
)
