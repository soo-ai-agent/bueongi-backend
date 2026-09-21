package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

// items 는 원천마다 배열과 `{item:…}` 이 섞여 와 JsonNode 로 받고 클라이언트가 정규화한다.
// 0건·오류 응답은 칸을 생략하고 오므로 전부 null 이 가능하다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrBody(
    val items: JsonNode? = null,
    val totalCount: Long? = null,
)
