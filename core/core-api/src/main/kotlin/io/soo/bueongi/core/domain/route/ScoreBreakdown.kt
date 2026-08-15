package io.soo.bueongi.core.domain.route

data class ScoreBreakdown(
    val routeKm: Double,
    val cctvDensity: Double,
    val lampDensity: Double,
    val bellDensity: Double,
    val safehouseCount: Int,
    val policeCount: Int,
    val safePathOverlap: Double,
)
