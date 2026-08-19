package io.soo.bueongi.client.kakao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** 외부 응답 규격 — 주소 검색 결과 한 건. x=경도, y=위도(문자열로 온다). */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KakaoAddressDocument(
    // 원천이 좌표를 비워 보내는 문서가 있어 null 이 가능함
    val x: String? = null,
    // 원천이 좌표를 비워 보내는 문서가 있어 null 이 가능함
    val y: String? = null,
)
