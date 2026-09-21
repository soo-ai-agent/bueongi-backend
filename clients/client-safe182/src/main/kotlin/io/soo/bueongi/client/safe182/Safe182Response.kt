package io.soo.bueongi.client.safe182

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// result 가 "00" 이어야 정상이고 "80" 은 일일 조회 한도 초과다.
// 오류·0건·통신 중단 응답은 칸을 생략하고 오므로 전부 null 이 가능하다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Safe182Response(
    val result: String? = null,
    val msg: String? = null,
    val totalCount: Long? = null,
    val list: List<Safe182Row>? = null,
)
