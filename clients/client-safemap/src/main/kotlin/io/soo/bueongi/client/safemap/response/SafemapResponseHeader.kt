package io.soo.bueongi.client.safemap.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 원천이 코드를 생략하는 응답이 있어 null 이 가능하다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponseHeader(
    val resultCode: String? = null,
)
