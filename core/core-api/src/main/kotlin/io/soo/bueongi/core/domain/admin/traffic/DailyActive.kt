package io.soo.bueongi.core.domain.admin.traffic

data class DailyActive(
    val dateEpochMs: Long,
    val distinctClients: Int,
)
