package io.soo.bueongi.client.safe182

// 좌표만 필수다 — 나머지는 원천이 칸을 비워 보내는 행이 있어 null 이 가능하다.
data class ChildSafeHousePoint(
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val address: String? = null,
    val phone: String? = null,
)
