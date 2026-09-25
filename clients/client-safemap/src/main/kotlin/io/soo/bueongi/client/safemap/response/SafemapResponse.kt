package io.soo.bueongi.client.safemap.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 통신이 잘리거나 오류인 응답은 칸을 생략하고 오므로 전부 null 이 가능하다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponse(val header: SafemapResponseHeader? = null, val body: SafemapResponseBody? = null)
