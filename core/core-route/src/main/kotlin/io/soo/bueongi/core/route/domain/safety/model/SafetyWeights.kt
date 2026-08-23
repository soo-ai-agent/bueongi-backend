package io.soo.bueongi.core.route.domain.safety.model

/** 안전 점수 항목별 가중치. 합이 1.0 이어야 점수가 0~100 계약을 지킨다. */
data class SafetyWeights(
    val cctv: Double,
    val lamp: Double,
    val bell: Double,
    val safehouse: Double,
    val police: Double,
    val safePath: Double,
) {

    val sum: Double
        get() = cctv + lamp + bell + safehouse + police + safePath
}
