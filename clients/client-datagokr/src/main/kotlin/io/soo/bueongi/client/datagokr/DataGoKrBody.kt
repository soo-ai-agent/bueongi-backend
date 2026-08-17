package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

/** items 는 배열/`{item:...}` 두 형태가 있어 [JsonNode] 로 받고 클라이언트가 정규화한다. */
/** 외부 응답 규격 — 응답 body — items 는 원천마다 배열/단건이 섞여 JsonNode 로 받는다. 원천이 어떤 필드든 생략할 수 있어 전 필드 null 이 가능함. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrBody(val items: JsonNode? = null, val totalCount: Long? = null)
