package io.soo.bueongi.client.kakao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** 검증 통과한 Kakao 장소 1건(클라이언트 모듈 소유 모델). */
data class KakaoPlace(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
)
