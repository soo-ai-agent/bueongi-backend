package io.soo.bueongi.client.safetydata.streetlamp

// 좌표만 필수다 — 주소·시군구는 원천이 비워 보내는 행이 있어 null 이 가능하다.
data class StreetLampPoint(
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    val sigungu: String? = null,
)
