package io.soo.bueongi.core.route.domain.model

// 프로퍼티 이름이 전부 프론트 응답 계약이라 이름을 바꾸지 않는다(RouteOptionWireContractTest).
// turnType 규격: 11=직진 12=좌회전 13=우회전 14=U턴 125=육교 126=지하보도 211=출발 212=도착.
data class NavigationStep(
    val index: Int,
    val lat: Double,
    val lng: Double,
    val description: String,
    val turnType: Int,
    val distanceMeters: Int,
    val timeSeconds: Int,
    val pointType: String,
)
