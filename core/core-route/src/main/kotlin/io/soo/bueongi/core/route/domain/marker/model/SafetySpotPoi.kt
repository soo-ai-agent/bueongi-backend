package io.soo.bueongi.core.route.domain.marker.model

// x·y 는 위경도가 아니라 프론트 미니맵용 0~100 백분율 좌표다(프론트 계약).
data class SafetySpotPoi(
    val type: SafetySpotPoiType,
    val x: Int,
    val y: Int,
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val purpose: String? = null,
    val cameraCount: Int? = null,
    val address: String? = null,
    val phone: String? = null,
)
