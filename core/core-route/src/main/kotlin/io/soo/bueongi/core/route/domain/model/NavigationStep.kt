package io.soo.bueongi.core.route.domain.model

// 프로퍼티명 전부가 프론트 응답 계약이다 — 리네임 금지(RouteOptionWireContractTest).
// turnType 규격: 11=직진 12=좌회전 13=우회전 14=U턴 125=육교 126=지하보도 211=출발 212=도착.
data class NavigationStep(
    val index: Int,
    val lat: Double,
    val lng: Double,
    val description: String,
    val turnType: Int,
    val distanceM: Int,
    val timeS: Int,
    val pointType: String,
)
