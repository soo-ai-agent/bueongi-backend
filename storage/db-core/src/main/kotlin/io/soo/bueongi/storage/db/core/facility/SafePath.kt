package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType

/**
 * 안심귀갓길 1건(좌표열). CDN safepath items 가 좌표열(coords)을 가지면 그대로,
 * 단일 점만 있으면 1점짜리 경로로 적재한다(겹침 계산에서 단일 점은 길이 0 → 보너스 0).
 */
data class SafePath(
    val coords: List<GeoPoint>,
    val name: String? = null,
)
