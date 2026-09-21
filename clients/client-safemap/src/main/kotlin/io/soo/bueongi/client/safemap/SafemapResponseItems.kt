package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 원천이 배열을 `items` 안에 한 번 더 감싼다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponseItems(
    // 결과가 0건인 응답은 item 을 생략해 null 이 가능함
    val item: List<SafemapPoliceRow>? = null,
)
