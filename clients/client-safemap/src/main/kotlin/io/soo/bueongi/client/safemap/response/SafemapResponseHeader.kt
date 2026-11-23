package io.soo.bueongi.client.safemap.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

// 원천이 코드를 생략하는 응답이 있어 빈 값으로 받는다 — 빈 코드는 정상으로 본다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponseHeader(
    @JsonSetter(nulls = Nulls.AS_EMPTY) val resultCode: String = "",
)
