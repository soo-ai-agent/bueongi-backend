package io.soo.bueongi.core.route.domain.marker.model

import io.soo.bueongi.core.enums.SafetySpotPoiType

/**
 * [x]·[y] 는 위경도가 아니라 프론트 미니맵용 0~100 백분율 좌표다 — 필드명은 프론트 계약이라 리네임 금지.
 * 부가 정보는 종류마다 있고 없다 — 카메라 대수는 CCTV, 전화는 지구대에만.
 */
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
