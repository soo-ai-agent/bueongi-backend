package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** 외부 응답 규격 — 본문. 행은 `items.item` 두 겹 아래 있다. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponseBody(
    // 결과가 0건인 응답은 items 를 생략해 null 이 가능함
    val items: SafemapResponseItems? = null,
    // 오류 응답에는 건수가 실리지 않아 null 이 가능함
    val totalCount: Long? = null,
)
