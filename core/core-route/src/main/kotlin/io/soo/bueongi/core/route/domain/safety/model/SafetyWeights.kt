package io.soo.bueongi.core.route.domain.safety.model

data class SafetyWeights(
    val cctv: Double,
    val lamp: Double,
    val bell: Double,
    val safehouse: Double,
    val police: Double,
    val safePath: Double,
) {
    val byItem: Map<SafetyItem, Double> =
        mapOf(
            SafetyItem.CCTV to cctv,
            SafetyItem.LAMP to lamp,
            SafetyItem.BELL to bell,
            SafetyItem.SAFEHOUSE to safehouse,
            SafetyItem.POLICE to police,
            SafetyItem.SAFE_PATH to safePath,
        )

    val sum: Double
        get() = byItem.values.sum()
}
