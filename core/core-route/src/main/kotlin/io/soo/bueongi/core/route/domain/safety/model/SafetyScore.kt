package io.soo.bueongi.core.route.domain.safety.model

data class SafetyScore(
    // 경로 주변에 안심시설 자료가 하나도 없으면 점수를 낼 수 없어서, 0점과 구분해 null 로 둔다.
    val score: Int?,
    val scoreDetail: SafetyScoreDetail,
) {
    val rank: Int
        get() = score ?: 0
}
