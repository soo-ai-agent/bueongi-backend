package io.soo.bueongi.client.safetydata.streetlamp.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

// totalCount 가 header 가 아닌 최상위에 있다(실측).
// 오류 응답은 header·totalCount 를 생략하고 온다 — 목록은 0건도 빈 목록으로 받는다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class StreetLampResponse(
    val header: StreetLampResponseHeader? = null,
    val totalCount: Long? = null,
    @JsonSetter(nulls = Nulls.AS_EMPTY) val body: List<StreetLampRow> = emptyList(),
)
