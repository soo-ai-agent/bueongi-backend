package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

/** 외부 응답 규격 — 응답 header — resultCode 로 성공 여부를 판정한다. 원천이 어떤 필드든 생략할 수 있어 전 필드 null 이 가능함. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrHeader(val resultCode: String? = null, val resultMsg: String? = null)
