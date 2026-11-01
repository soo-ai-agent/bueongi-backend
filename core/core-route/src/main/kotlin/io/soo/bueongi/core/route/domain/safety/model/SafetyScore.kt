package io.soo.bueongi.core.route.domain.safety.model

data class SafetyScore(
    val score: Int?,
    val scoreDetail: SafetyScoreDetail,
) {
    val rank: Int
        get() = score ?: 0
}
