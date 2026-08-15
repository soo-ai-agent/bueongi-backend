package io.soo.bueongi.core.share.domain

import java.time.Instant

/** 공유 세션 생성 결과. 응답 DTO 변환은 컨트롤러가 한다. */
data class ShareCreated(
    val token: String,
    val ownerSecret: String,
    val shareUrl: String,
    val expiresAt: Instant,
)
