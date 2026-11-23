package io.soo.bueongi.storage.db.share.model

import java.time.Instant

// PII(계정·전화번호 등)는 저장하지 않는다 — 비식별 토큰 URL 공유 모델.
// location·route 는 앱이 아직 올리지 않았으면, expectedArrivalAt 은 생성 때 eta 를 받지 않았으면(미도착 감시 제외) null 이다.
data class ShareSession(
    val token: String,
    // 공유 URL·조회 응답에 절대 포함 금지 — 앱만 보유하는 쓰기/종료 권한 비밀.
    val ownerSecret: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val location: StoredLocation? = null,
    val route: StoredRoute? = null,
    val expectedArrivalAt: Instant? = null,
) {
    fun withLocation(location: StoredLocation): ShareSession {
        return copy(location = location)
    }

    fun withRoute(route: StoredRoute): ShareSession {
        return copy(route = route)
    }

    fun isExpired(now: Instant): Boolean {
        return !now.isBefore(expiresAt)
    }
}
