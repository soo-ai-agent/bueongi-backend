package io.soo.bueongi.client.safemap.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 행은 `items.item` 두 겹 아래 있다.
// 0건·오류 응답은 칸을 생략하고 오므로 전부 null 이 가능하다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponseBody(
    val items: SafemapResponseItems? = null,
    val totalCount: Long? = null,
)
