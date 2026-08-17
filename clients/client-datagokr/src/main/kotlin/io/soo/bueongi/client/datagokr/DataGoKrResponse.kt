package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

/** 외부 응답 규격 — 응답 최상위(response 키 아래). 원천이 어떤 필드든 생략할 수 있어 전 필드 null 이 가능함. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrResponse(val header: DataGoKrHeader? = null, val body: DataGoKrBody? = null)
