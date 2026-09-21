package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 1741000 계열은 `response` 로 한 겹 더 감싸고 표준데이터 계열은 감싸지 않는다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrEnvelope(
    // 표준데이터 계열은 이 겹이 없어 null 이 가능함
    val response: DataGoKrResponse? = null,
    // 1741000 계열은 response 안에 있어 이 자리는 null 이 가능함
    val header: DataGoKrHeader? = null,
    // 1741000 계열은 response 안에 있어 이 자리는 null 이 가능함
    val body: DataGoKrBody? = null,
) {
    fun payload(): DataGoKrResponse = response ?: DataGoKrResponse(header, body)
}
