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
    /** 점수를 깎은 우범지역 수. 점수가 낮은 이유를 화면이 설명할 수 있어야 해 함께 내보낸다. */
    val crimeProneAreaCount: Int,
)
