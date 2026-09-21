package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// 좌표는 EPSG:3857 로 오지만(실측) 문서에 명시가 없어 값 범위로 자동판별한다. 깨진 행만 버리려고 문자열로 받는다.
// 원천이 어느 칸이든 비워 보내는 행이 있어 전부 null 이 가능하다 — 1,000건 기준 좌표 80건, 전화 289건, 시군구 70건이 비어 있다(실측).
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapPoliceRow(
    val x: String? = null,
    val y: String? = null,
    @param:JsonProperty("fclty_nm") val name: String? = null,
    @param:JsonProperty("rn_adres") val roadAddress: String? = null,
    @param:JsonProperty("telno") val phone: String? = null,
    @param:JsonProperty("sgg_cd") val sigungu: String? = null,
)
