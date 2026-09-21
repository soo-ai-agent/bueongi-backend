package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 원천이 코드나 메시지를 생략하는 응답이 있어 전부 null 이 가능하다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponseHeader(
    val resultCode: String? = null,
    val resultMsg: String? = null,
)
