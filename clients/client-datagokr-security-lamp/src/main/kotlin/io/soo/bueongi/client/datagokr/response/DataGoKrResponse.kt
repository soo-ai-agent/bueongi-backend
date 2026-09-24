package io.soo.bueongi.client.datagokr.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 통신이 잘리거나 오류인 응답은 칸을 생략하고 오므로 전부 null 이 가능하다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrResponse(
    val header: DataGoKrHeader? = null,
    val body: DataGoKrBody? = null,
)
