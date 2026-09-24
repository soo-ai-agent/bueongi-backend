package io.soo.bueongi.client.safetydata.streetlamp.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// 좌표계가 문서에 없어 값 범위로 자동판별한다. 깨진 행만 버리려고 문자열로 받는다(Double 이면 페이지 전체가 깨진다).
// 원천이 어느 칸이든 비워 보내는 행이 있어 전부 null 이 가능하다 — 주소는 1,500건 중 1,288건이 비어 있다(실측).
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class StreetLampRow(
    @param:JsonProperty("XMAP_CRTS") val x: String? = null,
    @param:JsonProperty("YMAP_CRTS") val y: String? = null,
    @param:JsonProperty("ROAD_NM_ADDR") val roadAddress: String? = null,
    @param:JsonProperty("ADDR") val lotAddress: String? = null,
    @param:JsonProperty("STDG_SGG_CD") val sigungu: String? = null,
)
