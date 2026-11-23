package io.soo.bueongi.storage.db.share.model

import java.time.Instant

// 남은 거리·시간은 앱이 보내지 않으면 null 이다 — 0 과 달리 도착 추정(isLikelyArrived)이 거리 조건을 건너뛴다.
data class StoredLocation(
    val lat: Double,
    val lng: Double,
    val updatedAt: Instant,
    val remainingMeters: Double? = null,
    val remainingSeconds: Double? = null,
)
