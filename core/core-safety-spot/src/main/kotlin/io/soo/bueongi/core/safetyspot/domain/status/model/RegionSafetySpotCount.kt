package io.soo.bueongi.core.safetyspot.domain.status.model

import io.soo.bueongi.core.enums.SafetySpotType

/** 자치단체 한 곳의 종류별 적재 건수. */
data class RegionSafetySpotCount(
    val code: String,
    val sido: String,
    val name: String,
    val countsByType: Map<SafetySpotType, Int>,
    val total: Int,
)
