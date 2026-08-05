package io.soo.bueongi.storage.db.core.share

import java.time.Instant

/**
 * 저장된 위치 1건. 좌표 + 갱신시각에 더해, 앱이 길안내 중 보고한 목적지까지의 잔여 거리/시간(선택)을 담는다.
 * Redis `location:{token}` HASH 와 1:1 대응하며, 공유 상한 만료(expiresAt)와 동일 TTL 로 함께 사라진다.
 */
data class StoredLocation(
    val lat: Double,
    val lng: Double,
    val updatedAt: Instant,
    /** 목적지까지 남은 거리(미터). 앱이 보고했을 때만 채워지고, 아니면 null. */
    val remainingM: Double? = null,
    /** 목적지까지 남은 예상 시간(초). 앱이 보고했을 때만 채워지고, 아니면 null. */
    val remainingS: Double? = null,
)
