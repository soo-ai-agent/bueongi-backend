package io.soo.bueongi.client.safetydata.streetlamp.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

// totalCount 가 header 가 아닌 최상위에 있다(실측).
// 오류 응답은 header·totalCount 를 생략하고 온다 — header·목록은 빈 값으로 받고, totalCount 의 null 은
// "총계를 안 줬다" 라 0 과 달리 페이지 크기로 끝을 판단한다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class StreetLampResponse(
    @JsonSetter(nulls = Nulls.AS_EMPTY) val header: StreetLampResponseHeader = StreetLampResponseHeader(),
    val totalCount: Long? = null,
    @JsonSetter(nulls = Nulls.AS_EMPTY) val body: List<StreetLampRow> = emptyList(),
)
