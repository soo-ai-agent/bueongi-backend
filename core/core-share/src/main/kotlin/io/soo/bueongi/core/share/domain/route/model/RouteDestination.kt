package io.soo.bueongi.core.share.domain.route.model

data class RouteDestination(
    // 목적지 이름을 정하지 않으면 null 이 가능함
    val name: String? = null,
    val lat: Double,
    val lng: Double,
)
