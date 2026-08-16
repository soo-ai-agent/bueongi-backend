package io.soo.bueongi.storage.db.core.share.model

data class StoredRouteDestination(
    // 목적지 이름을 정하지 않으면 null 이 가능함
    val name: String?,
    val lat: Double,
    val lng: Double,
)
