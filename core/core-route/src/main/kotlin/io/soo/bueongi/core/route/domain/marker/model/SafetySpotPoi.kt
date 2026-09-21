package io.soo.bueongi.core.route.domain.marker.model

// x·y 는 위경도가 아니라 프론트 미니맵용 0~100 백분율 좌표다(프론트 계약).
// 문자열 칸은 원천이 비워 보낸 것을 여기서 빈 값으로 메운다 — 받는 쪽이 전부 truthy 로 읽는다.
// cameraCount 만 예외로 null 을 유지한다 — 웹 보호자 뷰가 `!= null` 로 분기해
// 0 을 보내면 카메라가 없는 시설에도 "카메라 0대" 배지가 붙는다(RouteMap.tsx).
data class SafetySpotPoi(
    val type: SafetySpotPoiType,
    val x: Int,
    val y: Int,
    val lat: Double,
    val lng: Double,
    val name: String = "",
    val purpose: String = "",
    val cameraCount: Int? = null,
    val address: String = "",
    val phone: String = "",
)
