package io.soo.bueongi.client.safe182.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

// result 가 "00" 이어야 정상이고 "80" 은 일일 조회 한도 초과다.
// 오류·통신 중단 응답은 result·msg·totalCount 를 생략하고 온다 — 목록은 0건도 빈 목록으로 받는다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Safe182Response(
    val result: String? = null,
    val msg: String? = null,
    val totalCount: Long? = null,
    @JsonSetter(nulls = Nulls.AS_EMPTY) val list: List<Safe182Row> = emptyList(),
)
