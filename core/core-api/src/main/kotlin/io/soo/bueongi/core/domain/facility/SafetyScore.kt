package io.soo.bueongi.core.domain.facility

data class SafetyScore(
    val score: Int,
    val breakdown: SafetyBreakdown,
)
