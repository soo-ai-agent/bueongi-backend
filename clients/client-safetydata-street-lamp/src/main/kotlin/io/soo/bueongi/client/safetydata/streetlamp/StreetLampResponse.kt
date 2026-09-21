package io.soo.bueongi.client.safetydata.streetlamp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// totalCount 가 header 가 아닌 최상위에 있다(실측).
// 오류 응답은 칸을 생략하고 오므로 전부 null 이 가능하다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class StreetLampResponse(
    val header: StreetLampResponseHeader? = null,
    val totalCount: Long? = null,
    val body: List<StreetLampRow>? = null,
)
