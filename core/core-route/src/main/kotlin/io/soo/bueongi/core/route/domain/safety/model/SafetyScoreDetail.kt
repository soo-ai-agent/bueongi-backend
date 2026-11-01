package io.soo.bueongi.core.route.domain.safety.model

data class SafetyScoreDetail(
    val routeKm: Double,
    val cctvDensity: Double,
    val lampDensity: Double,
    val bellDensity: Double,
    val safehouseCount: Int,
    val policeCount: Int,
    val safePathOverlap: Double,
    val crimeProneAreaCount: Int,
    val scoredItems: List<String>,
    val unscoredItems: List<String>,
    val scoredItemRatio: Double,
)
