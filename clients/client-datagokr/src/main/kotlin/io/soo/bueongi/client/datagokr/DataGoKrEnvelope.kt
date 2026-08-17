package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

/** 외부 응답 규격 — 1741000 계열의 바깥 포장 — 표준데이터 계열에는 없다. 원천이 어떤 필드든 생략할 수 있어 전 필드 null 이 가능함. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrEnvelope(
    val response: DataGoKrResponse? = null,
    val header: DataGoKrHeader? = null,
    val body: DataGoKrBody? = null,
) {
    fun payload(): DataGoKrResponse = response ?: DataGoKrResponse(header, body)
}
