package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/** 가로등 1건(WGS84 변환·검증 완료). 도메인(FacilityPoint) 매핑은 상위(core-api)가 한다. */
data class SafetydataStreetlight(
    val lat: Double,
    val lng: Double,
    /** 도로명주소(ROAD_NM_ADDR) 우선, 없으면 지번주소(ADDR). */
    val address: String? = null,
    /** 법정동 시군구코드(STDG_SGG_CD). */
    val sigungu: String? = null,
)
