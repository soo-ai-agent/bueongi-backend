package io.soo.bueongi.storage.db.core.share.model

data class StoredRoute(
    val destination: StoredRouteDestination,
    val path: List<StoredCoord>,
    val markers: List<StoredMarker>,
    // 프론트가 보내지 않으면 null 이 가능함
    val totalDistanceM: Int?,
    // 프론트가 보내지 않으면 null 이 가능함
    val totalTimeS: Int?,
    /** 경로 종류(safe|fast|main) — 미전송 구세션은 null(하위호환). */
    val type: String? = null,
    // 데이터 출처를 모르면 null 이 가능함
    val provenance: String? = null,
)
