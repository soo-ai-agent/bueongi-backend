package io.soo.bueongi.core.share.domain.route.model

/** 목적지 이름은 사용자가 지정하지 않을 수 있다. */
data class RouteDestination(
    val name: String? = null,
    val lat: Double,
    val lng: Double,
)
