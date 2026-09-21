package io.soo.bueongi.core.route.domain.safety.model

data class SafetyWeights(
    val cctv: Double,
    val lamp: Double,
    val bell: Double,
    val safehouse: Double,
    val police: Double,
    val safePath: Double,
) {
    val byAxis: Map<SafetyAxis, Double> =
        mapOf(
            SafetyAxis.CCTV to cctv,
            SafetyAxis.LAMP to lamp,
            SafetyAxis.BELL to bell,
            SafetyAxis.SAFEHOUSE to safehouse,
            SafetyAxis.POLICE to police,
            SafetyAxis.SAFE_PATH to safePath,
        )

    val sum: Double
        get() = byAxis.values.sum()
}
