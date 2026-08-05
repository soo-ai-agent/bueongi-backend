package io.soo.bueongi.client.datagokr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/** 조회서비스 한 행의 정규화 결과(WGS84). 도메인(FacilityPoint) 매핑·좌표 범위 검증은 상위(core-api)가 한다. */
data class DataGoKrPoint(
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val address: String? = null,
    val sigungu: String? = null,
)
