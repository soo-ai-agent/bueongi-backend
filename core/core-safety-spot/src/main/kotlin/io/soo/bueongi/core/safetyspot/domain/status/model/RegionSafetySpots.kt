package io.soo.bueongi.core.safetyspot.domain.status.model

import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint

/**
 * 자치단체 한 곳의 시설 지점 목록.
 *
 * [total] 은 자르기 **전** 전체 건수다 — [spots] 만 내보내면 화면이 "다 보여 준 것"으로 오해한다.
 */
data class RegionSafetySpots(
    val code: String,
    val sido: String,
    val name: String,
    val total: Int,
    val spots: List<SafetySpotPoint>,
)
