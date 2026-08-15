package io.soo.bueongi.client.kakao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoKeywordResponse(
    val documents: List<KakaoPlaceDocument> = emptyList(),
)
