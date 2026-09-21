package io.soo.bueongi.core.route.domain.marker.model

/**
 * [x]·[y] 는 위경도가 아니라 프론트 미니맵용 0~100 백분율 좌표다 — 필드명은 프론트 계약이라 리네임 금지.
 */
data class SafetySpotPoi(
    val type: SafetySpotPoiType,
    val x: Int,
    val y: Int,
    val lat: Double,
    val lng: Double,
    // 출발·도착 마커는 null 이 가능함
    val name: String? = null,
    // CCTV 가 아닌 시설은 null 이 가능함
    val purpose: String? = null,
    // CCTV 가 아닌 시설은 null 이 가능함
    val cameraCount: Int? = null,
    // 지구대가 아닌 시설은 null 이 가능함
    val address: String? = null,
    // 지구대가 아닌 시설은 null 이 가능함
    val phone: String? = null,
)
