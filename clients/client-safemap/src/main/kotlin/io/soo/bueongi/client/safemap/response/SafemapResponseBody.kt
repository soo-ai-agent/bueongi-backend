package io.soo.bueongi.client.safemap.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

// 0건·오류 응답은 칸을 생략하고 온다. totalCount 의 null 은 "총계를 안 줬다" 라 0 과 달리 페이지 크기로 끝을 판단한다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponseBody(
    @JsonSetter(nulls = Nulls.AS_EMPTY) val items: SafemapResponseItems = SafemapResponseItems(),
    val totalCount: Long? = null,
)
