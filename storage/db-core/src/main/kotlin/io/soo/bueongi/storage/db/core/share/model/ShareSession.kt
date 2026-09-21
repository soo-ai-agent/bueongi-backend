package io.soo.bueongi.storage.db.core.share.model

import java.time.Instant

// PII(계정·전화번호 등)는 저장하지 않는다 — 비식별 토큰 URL 공유 모델.
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
    fun withLocation(location: StoredLocation): ShareSession = copy(location = location)

    fun withRoute(route: StoredRoute): ShareSession = copy(route = route)

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)

    fun isOverdue(now: Instant): Boolean {
        val expected: Instant = expectedArrivalAt ?: return false
        return now.isAfter(expected.plusSeconds(OVERDUE_SECONDS))
    }

    fun isLocationStopped(now: Instant): Boolean {
        val reported: StoredLocation = location ?: return true
        return now.isAfter(reported.updatedAt.plusSeconds(LOCATION_STOPPED_SECONDS))
    }

    fun isMissedArrivalSuspect(now: Instant): Boolean = isOverdue(now) && isLocationStopped(now)

    companion object {
        const val OVERDUE_SECONDS: Long = 300L

        const val LOCATION_STOPPED_SECONDS: Long = 90L
    }
}
