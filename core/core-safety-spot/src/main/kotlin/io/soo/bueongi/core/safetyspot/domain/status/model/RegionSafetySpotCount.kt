package io.soo.bueongi.core.safetyspot.domain.status.model

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

/** 자치단체 한 곳의 종류별 적재 건수. */
data class RegionSafetySpotCount(
    val code: String,
    val sido: String,
    val name: String,
    val countsByType: Map<SafetySpotType, Int>,
    val total: Int,
    /** 이 지역 지점들의 평균 좌표. 쓸 수 있는 좌표가 한 점도 없으면 null 이 가능함 */
    val center: GeoPoint?,
)
