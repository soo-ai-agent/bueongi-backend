package io.soo.bueongi.core.safetyspot.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

// 모바일 앱 NearbyPolice 와 동형 — 필드명(tel·distance_m) 리네임 금지.
data class NearestPolice(
    val name: String?,
    val address: String?,
    val tel: String?,
    val lat: Double,
    val lng: Double,
    @param:JsonProperty("distance_m")
    val distanceM: Int,
)
