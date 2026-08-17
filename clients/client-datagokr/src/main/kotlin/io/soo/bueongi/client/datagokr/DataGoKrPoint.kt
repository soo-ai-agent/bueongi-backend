package io.soo.bueongi.client.datagokr

/** 통신 응답을 우리 말로 옮긴 결과 — 이 타입만 모듈 밖으로 나간다. */
data class DataGoKrPoint(
    val lat: Double,
    val lng: Double,
    // 원천이 이름·주소·시군구를 제공하지 않는 행이 있어 null 이 가능함
    val name: String? = null,
    val address: String? = null,
    val sigungu: String? = null,
    // 원천이 관리기관 전화를 주지 않는 종류가 있어 null 이 가능함
    val phone: String? = null,
)
