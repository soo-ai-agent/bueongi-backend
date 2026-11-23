package io.soo.bueongi.client.datagokr.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

// 이 패키지의 응답 칸은 원천이 오류·0건 응답에서 생략하거나 null 로 보내 빈 값으로 받는다.
// 1741000 계열은 `response` 로 한 겹 더 감싸고, 표준데이터 계열은 감싸지 않는다 — response 의 null 이 그 갈림이다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrEnvelope(
    val response: DataGoKrResponse? = null,
    @JsonSetter(nulls = Nulls.AS_EMPTY) val header: DataGoKrHeader = DataGoKrHeader(),
    @JsonSetter(nulls = Nulls.AS_EMPTY) val body: DataGoKrBody = DataGoKrBody(),
) {
    fun payload(): DataGoKrResponse = response ?: DataGoKrResponse(header, body)
}
