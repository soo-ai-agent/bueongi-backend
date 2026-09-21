package io.soo.bueongi.core.route.domain.safety.model

data class SafetyBreakdown(
    val routeKm: Double,
    val cctvDensity: Double,
    val lampDensity: Double,
    val bellDensity: Double,
    val safehouseCount: Int,
    val policeCount: Int,
    val safePathOverlap: Double,
    val crimeProneAreaCount: Int,
    val scoredAxes: List<String>,
    val unscoredAxes: List<String>,
    val axisCoverage: Double,
)
