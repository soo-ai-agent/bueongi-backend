package io.soo.bueongi.storage.db.core.share.model

import java.time.Instant

/**
 * PII(계정·전화번호 등)는 저장하지 않는다 — 비식별 토큰 URL 공유 모델.
 * 도착 예정 시각은 사용자가 ETA 를 넣었을 때만 있다.
 */
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
    fun withLocation(location: StoredLocation): ShareSession =
        copy(location = location)

    fun withRoute(route: StoredRoute): ShareSession =
        copy(route = route)

    fun isExpired(now: Instant): Boolean =
        !now.isBefore(expiresAt)

    /** 예정 도착 시간을 유예 이상 넘겼는가. 도착 예정이 없으면 판단 대상이 아니다. */
    fun isOverdue(now: Instant): Boolean {
        val expected: Instant = expectedArrivalAt ?: return false
        return now.isAfter(expected.plusSeconds(OVERDUE_SECONDS))
    }

    /** 위치 보고가 끊겼는가. 한 번도 보고가 없으면 끊긴 것으로 본다. */
    fun isLocationStopped(now: Instant): Boolean {
        val reported: StoredLocation = location ?: return true
        return now.isAfter(reported.updatedAt.plusSeconds(LOCATION_STOPPED_SECONDS))
    }

    fun isMissedArrivalSuspect(now: Instant): Boolean =
        isOverdue(now) && isLocationStopped(now)

    companion object {

        const val OVERDUE_SECONDS: Long = 300L

        const val LOCATION_STOPPED_SECONDS: Long = 90L
    }
}
