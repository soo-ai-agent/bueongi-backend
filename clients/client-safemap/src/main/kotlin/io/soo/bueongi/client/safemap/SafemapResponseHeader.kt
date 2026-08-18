package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** 외부 응답 규격 — resultCode 로 성공 여부를 판정한다(정상 = "00"). */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponseHeader(
    // 원천이 코드를 생략하는 응답이 있어 null 이 가능함
    val resultCode: String? = null,
    // 정상 응답은 메시지를 생략하기도 해 null 이 가능함
    val resultMsg: String? = null,
)
