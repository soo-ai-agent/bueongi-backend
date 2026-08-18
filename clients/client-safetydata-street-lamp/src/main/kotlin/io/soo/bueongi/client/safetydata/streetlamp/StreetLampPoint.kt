package io.soo.bueongi.client.safetydata.streetlamp

/** 통신 응답을 우리 말로 옮긴 결과 — 이 타입만 모듈 밖으로 나간다. */
data class StreetLampPoint(
    val lat: Double,
    val lng: Double,
    // 원천이 주소를 비워 보내는 행이 많아 null 이 가능함
    val address: String? = null,
    // 원천이 시군구 코드를 생략하는 행이 있어 null 이 가능함
    val sigungu: String? = null,
)
