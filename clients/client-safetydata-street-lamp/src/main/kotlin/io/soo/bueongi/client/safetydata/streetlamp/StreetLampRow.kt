package io.soo.bueongi.client.safetydata.streetlamp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// 좌표계가 문서에 없어 값 범위로 자동판별한다. 깨진 행만 버리려고 문자열로 받는다(Double 이면 페이지 전체가 깨진다).
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class StreetLampRow(
    // 좌표를 비워 보내는 행이 있어 null 이 가능함
    @param:JsonProperty("XMAP_CRTS") val x: String? = null,
    // 좌표를 비워 보내는 행이 있어 null 이 가능함
    @param:JsonProperty("YMAP_CRTS") val y: String? = null,
    // 실응답 1,500건 중 1,288건이 주소를 비워 보내 null 이 가능함
    @param:JsonProperty("ROAD_NM_ADDR") val roadAddress: String? = null,
    // 도로명주소가 없을 때의 대체값이고 이쪽도 비어 오는 행이 있어 null 이 가능함
    @param:JsonProperty("ADDR") val lotAddress: String? = null,
    // 원천이 시군구 코드를 생략하는 행이 있어 null 이 가능함
    @param:JsonProperty("STDG_SGG_CD") val sigungu: String? = null,
)
