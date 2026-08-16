package io.soo.bueongi.core.share.domain.route.model

import com.fasterxml.jackson.annotation.JsonProperty

data class RouteMarker(
    val type: String,
    val lat: Double,
    val lng: Double,
    // 서버가 나중에 채우므로 받을 때는 null 이 가능함
    val x: Double? = null,
    // 서버가 나중에 채우므로 받을 때는 null 이 가능함
    val y: Double? = null,
    // 원천에 값이 없으면 null 이 가능함
    val name: String? = null,
    // CCTV 가 아닌 시설은 null 이 가능함
    val purpose: String? = null,
    @param:JsonProperty("camera_count")
    // CCTV 가 아닌 시설은 null 이 가능함
    val cameraCount: Int? = null,
    // 지구대가 아닌 시설은 null 이 가능함
    val address: String? = null,
    // 지구대가 아닌 시설은 null 이 가능함
    val phone: String? = null,
)
