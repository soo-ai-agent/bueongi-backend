package io.soo.bueongi.core.share.domain.model

import java.time.Instant

/**
 * 보호자 관찰 상태. 응답 DTO 변환은 컨트롤러가 한다.
 * 아직 보고가 없으면 위치·시각이 비어 있다.
 */
data class ShareWatchStatus(
    val watching: Boolean,
    val lat: Double? = null,
    val lng: Double? = null,
    val updatedAt: Instant? = null,
    val expired: Boolean = false,
    val remainingM: Double? = null,
    val remainingS: Double? = null,
)
