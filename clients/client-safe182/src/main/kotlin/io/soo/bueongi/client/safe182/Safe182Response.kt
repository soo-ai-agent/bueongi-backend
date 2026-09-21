package io.soo.bueongi.client.safe182

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// result 가 "00" 이어야 정상이고 "80" 은 일일 조회 한도 초과다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Safe182Response(
    // 통신이 잘린 응답에는 코드가 없어 null 이 가능함
    val result: String? = null,
    // 정상 응답은 메시지를 생략하기도 해 null 이 가능함
    val msg: String? = null,
    // 오류 응답에는 건수가 실리지 않아 null 이 가능함
    val totalCount: Long? = null,
    // 결과가 0건인 응답은 목록을 생략해 null 이 가능함
    val list: List<Safe182Row>? = null,
)
