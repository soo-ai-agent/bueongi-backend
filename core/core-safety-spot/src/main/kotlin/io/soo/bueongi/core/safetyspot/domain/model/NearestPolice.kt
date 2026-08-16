package io.soo.bueongi.core.safetyspot.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 프론트 NearbyPolice 계약과 동형 — 필드명(tel·distance_m)은 리네임 금지.
 */
data class NearestPolice(
    // 원천에 값이 없으면 null 이 가능함
    val name: String?,
    // 원천에 값이 없으면 null 이 가능함
    val address: String?,
    // 원천에 전화번호가 없는 지구대가 있어 null 이 가능함
    val tel: String?,
    val lat: Double,
    val lng: Double,
    @param:JsonProperty("distance_m")
    val distanceM: Int,
)
