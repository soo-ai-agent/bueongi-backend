package io.soo.bueongi.storage.db.share.store

import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredLocation
import io.soo.bueongi.storage.db.share.model.StoredRoute
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

// kotlin-jpa 플러그인(noArg)이 JPA 가 요구하는 무인자 생성자를 만들고, allOpen 이 @Entity 를 open 으로 연다.
@Entity
@Table(name = "share_session")
class ShareSessionEntity(
    @Id
    @Column(name = "token", length = 128)
    val token: String,
    @Column(name = "owner_secret", nullable = false, length = 128)
    val ownerSecret: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "expected_arrival_at")
    val expectedArrivalAt: Instant? = null,
    @Column(name = "lat")
    val lat: Double? = null,
    @Column(name = "lng")
    val lng: Double? = null,
    @Column(name = "location_updated_at")
    val locationUpdatedAt: Instant? = null,
    @Column(name = "remaining_meters")
    val remainingMeters: Double? = null,
    @Column(name = "remaining_seconds")
    val remainingSeconds: Double? = null,
    // 경로는 쓰임새가 통짜 저장·통짜 조회뿐이라 칸으로 펴지 않고 JSON 그대로 둔다.
    @Column(name = "route_json", length = ROUTE_JSON_MAX_LENGTH)
    val routeJson: String? = null,
    @Column(name = "viewed_at")
    var viewedAt: Instant? = null,
) {
    fun markViewed(now: Instant) {
        viewedAt = now
    }

    fun isExpired(now: Instant): Boolean {
        return !now.isBefore(expiresAt)
    }

    fun toShareSession(location: StoredLocation?, route: StoredRoute?): ShareSession {
        return ShareSession(
            token = token,
            ownerSecret = ownerSecret,
            createdAt = createdAt,
            expiresAt = expiresAt,
            location = location,
            route = route,
            expectedArrivalAt = expectedArrivalAt,
        )
    }

    fun locationOrNull(): StoredLocation? {
        val reportedLat: Double = lat ?: return null
        val reportedLng: Double = lng ?: return null
        val reportedAt: Instant = locationUpdatedAt ?: return null
        return StoredLocation(
            lat = reportedLat,
            lng = reportedLng,
            updatedAt = reportedAt,
            remainingMeters = remainingMeters,
            remainingSeconds = remainingSeconds,
        )
    }

    companion object {
        // 경로 JSON 은 좌표 수백 개를 담는다 — 1MB 면 넉넉하고 H2·PostgreSQL 모두 같은 선언으로 받는다.
        const val ROUTE_JSON_MAX_LENGTH: Int = 1_000_000

        fun from(session: ShareSession, routeJson: String?, viewedAt: Instant?): ShareSessionEntity {
            return ShareSessionEntity(
                token = session.token,
                ownerSecret = session.ownerSecret,
                createdAt = session.createdAt,
                expiresAt = session.expiresAt,
                expectedArrivalAt = session.expectedArrivalAt,
                lat = session.location?.lat,
                lng = session.location?.lng,
                locationUpdatedAt = session.location?.updatedAt,
                remainingMeters = session.location?.remainingMeters,
                remainingSeconds = session.location?.remainingSeconds,
                routeJson = routeJson,
                viewedAt = viewedAt,
            )
        }
    }
}
