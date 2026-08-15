package io.soo.bueongi.core.route.domain.safety.model

/** 안전 점수의 근거 수치. 그대로 RouteOption.breakdown 으로 나가는 응답 계약이다. */
data class SafetyBreakdown(
    val routeKm: Double,
    val cctvDensity: Double,
    val lampDensity: Double,
    val bellDensity: Double,
    val safehouseCount: Int,
    val policeCount: Int,
    val safePathOverlap: Double,
)
