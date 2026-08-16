package io.soo.bueongi.storage.db.core.share.model

/** 경로 메타는 프론트가 보낸 만큼만 저장한다. */
data class StoredRoute(
    val destination: StoredRouteDestination,
    val path: List<StoredCoord>,
    val markers: List<StoredMarker>,
    val totalDistanceM: Int?,
    val totalTimeS: Int?,
    /** 경로 종류(safe|fast|main) — 미전송 구세션은 null(하위호환). */
    val type: String? = null,
    val provenance: String? = null,
)
