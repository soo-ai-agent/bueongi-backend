package io.soo.bueongi.client.tmap

// Tmap turnType 규격: 11=직진 12=좌회전 13=우회전 14=U턴 125=육교 126=지하보도 211=출발 212=도착.
// 이 타입은 clients 모듈 안에서만 쓴다 — core 로는 NavigationStep 으로 바꿔 올린다(RouteFinder).
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
