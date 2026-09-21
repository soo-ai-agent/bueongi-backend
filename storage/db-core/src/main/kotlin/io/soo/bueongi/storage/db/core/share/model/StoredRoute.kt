package io.soo.bueongi.storage.db.core.share.model

data class StoredRoute(
    val destination: StoredRouteDestination,
    val path: List<StoredCoord>,
    val markers: List<StoredMarker>,
    val totalDistanceM: Int?,
    val totalTimeS: Int?,
    // 허용값 safe|fast|main — 이 필드를 보내지 않던 구세션이 있어 null 을 허용한다.
    val type: String? = null,
    val provenance: String? = null,
)
