package io.soo.bueongi.client.kakao

/** 통신 응답을 우리 말로 옮긴 결과 — 이 타입만 모듈 밖으로 나간다. */
data class KakaoAddressPoint(
    val lat: Double,
    val lng: Double,
)
