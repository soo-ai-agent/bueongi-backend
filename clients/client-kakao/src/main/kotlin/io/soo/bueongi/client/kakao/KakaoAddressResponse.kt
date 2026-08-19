package io.soo.bueongi.client.kakao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** 외부 응답 규격 — 주소 검색(`/v2/local/search/address.json`) 봉투. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KakaoAddressResponse(
    // 오류 응답에는 목록이 실리지 않아 null 이 가능함
    val documents: List<KakaoAddressDocument>? = null,
)
