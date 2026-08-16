package io.soo.bueongi.core.share.domain.route.model

import com.fasterxml.jackson.annotation.JsonProperty

/** 부가 정보는 종류마다 있고 없다 — 카메라 대수는 CCTV, 전화는 지구대에만. */
data class RouteMarker(
    val type: String,
    val lat: Double,
    val lng: Double,
    val x: Double? = null,
    val y: Double? = null,
    val name: String? = null,
    val purpose: String? = null,
    @JsonProperty("camera_count")
    val cameraCount: Int? = null,
    val address: String? = null,
    val phone: String? = null,
)
