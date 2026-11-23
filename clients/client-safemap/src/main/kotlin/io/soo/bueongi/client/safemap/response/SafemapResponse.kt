package io.soo.bueongi.client.safemap.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

// 통신이 잘리거나 오류인 응답은 칸을 생략하고 오므로 빈 껍데기로 받는다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponse(
    @JsonSetter(nulls = Nulls.AS_EMPTY) val header: SafemapResponseHeader = SafemapResponseHeader(),
    @JsonSetter(nulls = Nulls.AS_EMPTY) val body: SafemapResponseBody = SafemapResponseBody(),
)
