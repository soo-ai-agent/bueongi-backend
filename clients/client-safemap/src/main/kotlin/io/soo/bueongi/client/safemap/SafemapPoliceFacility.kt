package io.soo.bueongi.client.safemap

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/** 치안시설 1건(WGS84 변환·검증 완료). 도메인(FacilityPoint) 매핑은 상위(core-api)가 한다. */
data class SafemapPoliceFacility(
    val lat: Double,
    val lng: Double,
    /** 시설(관서)명 — fclty_nm (예: "역삼지구대"). */
    val name: String? = null,
    /** 도로명주소(rn_adres) 우선, 없으면 지번주소(adres). */
    val address: String? = null,
    /** 전화번호(telno) — 긴급 시트의 파출소 연결에 쓰인다. */
    val phone: String? = null,
    /** 시군구코드(sgg_cd). */
    val sigungu: String? = null,
)
