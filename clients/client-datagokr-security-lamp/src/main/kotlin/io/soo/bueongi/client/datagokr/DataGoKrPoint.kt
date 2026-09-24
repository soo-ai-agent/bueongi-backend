package io.soo.bueongi.client.datagokr

// 좌표만 필수다 — 나머지는 원천이 칸을 비우거나(이름·주소·전화) 컬럼 자체가 없어서(CCTV 의 시군구) null 이 가능하다.
data class DataGoKrPoint(
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val address: String? = null,
    val sigungu: String? = null,
    val phone: String? = null,
)
