package io.soo.bueongi.core.share.domain.model

import java.time.Instant

/** 위치·경로 갱신 결과. 응답 DTO 변환은 컨트롤러가 한다. */
data class ShareLocationUpdated(
    val updatedAt: Instant,
    val expiresAt: Instant,
)
