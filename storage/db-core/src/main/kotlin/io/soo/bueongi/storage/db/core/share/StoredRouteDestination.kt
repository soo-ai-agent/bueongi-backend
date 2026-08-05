package io.soo.bueongi.storage.db.core.share

import java.time.Instant

/** 경로 목적지(좌표 + 표시명). */
data class StoredRouteDestination(
    val name: String?,
    val lat: Double,
    val lng: Double,
)
