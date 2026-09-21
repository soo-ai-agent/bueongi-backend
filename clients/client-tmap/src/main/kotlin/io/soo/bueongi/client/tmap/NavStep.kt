package io.soo.bueongi.client.tmap

// Tmap turnType 규격: 11=직진 12=좌회전 13=우회전 14=U턴 125=육교 126=지하보도 211=출발 212=도착.
// 프로퍼티명 전부가 프론트 응답 계약이다 — 리네임 금지(core-api RouteOptionWireContractTest).
data class NavStep(
    val index: Int,
    val lat: Double,
    val lng: Double,
    val description: String,
    val turnType: Int,
    val distanceM: Int,
    val timeS: Int,
    val pointType: String,
)
