package io.soo.bueongi.core.ops.domain.traffic.model

data class EstimatedUsers(
    val today: Int,
    val last7d: Int,
    val last30d: Int,
    val callsWithoutClientId: Int,
    val newToday: Int,
    val returningToday: Int,
)
