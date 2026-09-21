package io.soo.bueongi.core.route.domain.marker.model

// x·y 는 위경도가 아니라 프론트 미니맵용 0~100 백분율 좌표다(프론트 계약).
// 원천이 비워 보낸 칸은 여기서 빈 값으로 메운다 — 앱이 null 과 빈 값을 구분하지 않는다.
data class SafetySpotPoi(
    val type: SafetySpotPoiType,
    val x: Int,
    val y: Int,
    val lat: Double,
    val lng: Double,
    val name: String = "",
    val purpose: String = "",
    val cameraCount: Int = 0,
    val address: String = "",
    val phone: String = "",
)
