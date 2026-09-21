package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponse(
    // 통신이 잘린 응답에는 헤더가 없어 null 이 가능함
    val header: SafemapResponseHeader? = null,
    // 오류 응답에는 본문이 실리지 않아 null 이 가능함
    val body: SafemapResponseBody? = null,
)
