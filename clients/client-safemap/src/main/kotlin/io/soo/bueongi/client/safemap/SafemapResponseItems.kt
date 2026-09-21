package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

// 원천이 배열을 `items` 안에 한 번 더 감싼다. 0건이면 item 을 생략하고 오므로 빈 목록으로 받는다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapResponseItems(
    @JsonSetter(nulls = Nulls.AS_EMPTY) val item: List<SafemapPoliceRow> = emptyList(),
)
