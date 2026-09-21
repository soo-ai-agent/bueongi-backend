package io.soo.bueongi.client.safemap

// 좌표만 필수다 — 나머지는 원천이 칸을 비워 보내는 행이 있어 null 이 가능하다(무인 치안센터는 전화가 없다).
data class SafemapPolicePoint(
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val sigungu: String? = null,
)
