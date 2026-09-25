package io.soo.bueongi.core.route.domain.safety.model

data class SafetyScore(val score: Int?, val breakdown: SafetyBreakdown) {
    val rank: Int
        get() = score ?: 0
}
