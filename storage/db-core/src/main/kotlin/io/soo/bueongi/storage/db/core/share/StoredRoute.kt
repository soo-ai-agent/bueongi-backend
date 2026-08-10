package io.soo.bueongi.storage.db.core.share

import java.time.Instant

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
