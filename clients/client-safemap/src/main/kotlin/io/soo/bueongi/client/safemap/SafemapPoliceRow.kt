package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// 좌표는 EPSG:3857 로 오지만(실측) 문서에 명시가 없어 값 범위로 자동판별한다. 깨진 행만 버리려고 문자열로 받는다.
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SafemapPoliceRow(
    // 좌표를 0 으로 채워 보내는 행이 있어(실측 1,000건 중 80건) null 이 가능함
    val x: String? = null,
    // 좌표를 0 으로 채워 보내는 행이 있어(실측 1,000건 중 80건) null 이 가능함
    val y: String? = null,
    // 원천이 관서명을 비워 보내는 행이 있어 null 이 가능함
    @param:JsonProperty("fclty_nm") val name: String? = null,
    // 원천이 도로명주소를 비워 보내는 행이 있어 null 이 가능함
    @param:JsonProperty("rn_adres") val roadAddress: String? = null,
    // 무인 치안센터처럼 전화가 없는 시설이 있어(실측 1,000건 중 289건) null 이 가능함
    @param:JsonProperty("telno") val phone: String? = null,
    // 원천이 시군구 코드를 생략하는 행이 있어(실측 1,000건 중 70건) null 이 가능함
    @param:JsonProperty("sgg_cd") val sigungu: String? = null,
)
