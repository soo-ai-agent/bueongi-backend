package io.soo.bueongi.client.safetydata.streetlamp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// totalCount 가 header 가 아닌 최상위에 있다(실측).
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class StreetLampResponse(
    // 원천이 헤더를 생략하는 응답이 있어 null 이 가능함
    val header: StreetLampResponseHeader? = null,
    // 오류 응답에는 건수가 실리지 않아 null 이 가능함
    val totalCount: Long? = null,
    // 오류 응답에는 본문이 실리지 않아 null 이 가능함
    val body: List<StreetLampRow>? = null,
)
