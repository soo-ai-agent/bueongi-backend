package io.soo.bueongi.storage.db.core.share

import java.time.Instant

/** PII(계정·전화번호 등)는 저장하지 않는다 — 비식별 토큰 URL 공유 모델. */
data class ShareSession(
    val token: String,
    /** 쓰기/종료 권한 비밀(앱만 보유) — 공유 URL·조회 응답에 절대 포함 금지(읽기/쓰기 분리). */
    val ownerSecret: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val location: StoredLocation? = null,
    val route: StoredRoute? = null,
    val expectedArrivalAt: Instant? = null,
) {
    fun withLocation(location: StoredLocation): ShareSession = copy(location = location)

    fun withRoute(route: StoredRoute): ShareSession = copy(route = route)

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)
}
