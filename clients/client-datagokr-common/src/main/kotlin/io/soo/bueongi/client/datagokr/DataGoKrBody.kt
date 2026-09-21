package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

// items 는 원천마다 배열과 `{item:…}` 이 섞여 와 JsonNode 로 받고 클라이언트가 정규화한다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DataGoKrBody(
    // 결과가 0건인 응답은 items 를 생략해 null 이 가능함
    val items: JsonNode? = null,
    // 오류 응답에는 건수가 실리지 않아 null 이 가능함
    val totalCount: Long? = null,
)
