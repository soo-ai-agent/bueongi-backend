package io.soo.bueongi.client.datagokr.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 이 패키지의 응답 타입은 전부 null 필드다 — 원천이 오류·0건 응답에서 칸을 생략하고 보낸다.
// 1741000 계열은 `response` 로 한 겹 더 감싸고, 표준데이터 계열은 감싸지 않는다 — 둘 중 한쪽만 채워진다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrEnvelope(
    val response: DataGoKrResponse? = null,
    val header: DataGoKrHeader? = null,
    val body: DataGoKrBody? = null,
) {
    fun payload(): DataGoKrResponse = response ?: DataGoKrResponse(header, body)
}
