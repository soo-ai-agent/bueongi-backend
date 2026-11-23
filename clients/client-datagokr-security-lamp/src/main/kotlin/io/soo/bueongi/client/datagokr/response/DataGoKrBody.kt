package io.soo.bueongi.client.datagokr.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode

// totalCount 의 null 은 "서버가 총계를 안 줬다" 다 — 0 과 달라 수집기가 페이지 크기로 끝을 판단한다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrBody(
    val items: JsonNode = NullNode.instance,
    val totalCount: Long? = null,
)
