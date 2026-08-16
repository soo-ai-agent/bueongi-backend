package io.soo.bueongi.storage.db.core.share.model

/** 목적지 이름은 사용자가 지정하지 않을 수 있다. */
data class StoredRouteDestination(
    val name: String?,
    val lat: Double,
    val lng: Double,
)
