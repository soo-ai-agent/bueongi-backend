package io.soo.bueongi.core.domain.facility

import com.fasterxml.jackson.annotation.JsonProperty

/** 프론트 NearbyPolice 계약과 동형 — 필드명(tel·distance_m)은 리네임 금지. */
data class NearestPolice(
    val name: String?,
    val address: String?,
    val tel: String?,
    val lat: Double,
    val lng: Double,
    @JsonProperty("distance_m")
    val distanceM: Int,
)
