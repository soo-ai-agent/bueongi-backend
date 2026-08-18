package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** 외부 응답 규격 — `response` 키 아래 본문. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrResponse(
    // 통신이 잘린 응답에는 헤더가 없어 null 이 가능함
    val header: DataGoKrHeader? = null,
    // 오류 응답에는 본문이 실리지 않아 null 이 가능함
    val body: DataGoKrBody? = null,
)
