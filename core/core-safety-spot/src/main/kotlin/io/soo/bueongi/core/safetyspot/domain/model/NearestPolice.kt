package io.soo.bueongi.core.safetyspot.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

// 모바일 앱 NearbyPolice 와 동형 — 필드명(tel·distance_m) 리네임 금지.
// 원천이 비워 보낸 칸은 빈 문자열로 내보낸다 — 받는 쪽이 전부 truthy 로 읽는다.
data class NearestPolice(
    val name: String,
    val address: String,
    val tel: String,
    val lat: Double,
    val lng: Double,
    @param:JsonProperty("distance_m")
    val distanceM: Int,
)
