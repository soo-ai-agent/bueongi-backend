package io.soo.bueongi.core.domain.share

import io.soo.bueongi.core.domain.share.route.RouteView


import io.soo.bueongi.storage.db.core.share.LocationStore
import io.soo.bueongi.storage.db.core.share.ShareSession
import io.soo.bueongi.storage.db.core.share.StoredLocation
import io.soo.bueongi.storage.db.core.share.StoredRoute
import io.soo.bueongi.core.support.error.ShareSessionNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

@Service
class ShareService(
    private val store: LocationStore,
    private val clock: Clock,
    @Value("\${bueongi.share.base-url}") private val baseUrl: String,
) {
    private val random: SecureRandom = SecureRandom()
    private val tokenEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun create(expiresInHours: Int?, etaSeconds: Int? = null): ShareCreated {
        val hours: Int = expiresInHours ?: DEFAULT_EXPIRES_HOURS
        val token: String = newToken()
        val ownerSecret: String = newToken()
        val now: Instant = clock.instant()
        val expiresAt: Instant = now.plusSeconds(hours.toLong() * SECONDS_PER_HOUR)
        val expectedArrivalAt: Instant? = etaSeconds?.let { now.plusSeconds(it.toLong() + MISSED_ARRIVAL_GRACE_SECONDS) }
        store.save(
            ShareSession(
                token = token,
                ownerSecret = ownerSecret,
                createdAt = now,
                expiresAt = expiresAt,
                location = null,
                expectedArrivalAt = expectedArrivalAt,
            ),
        )
        return ShareCreated(
            token = token,
            ownerSecret = ownerSecret,
            shareUrl = shareUrlFor(token),
            expiresAt = expiresAt,
        )
    }

    fun listActiveSessions(): List<ActiveShareView> {
        val now: Instant = clock.instant()
        return store.findActiveSessions()
            .map { session -> toActiveShareView(session, now) }
            .sortedWith(ACTIVE_SHARE_ORDER)
    }

    fun countActiveSessions(): Int = store.countActiveSessions()

    private fun toActiveShareView(session: ShareSession, now: Instant): ActiveShareView {
        val location: StoredLocation? = session.location
        val ttlSeconds: Long = Duration.between(now, session.expiresAt).seconds.coerceAtLeast(0)
        return ActiveShareView(
            token = session.token,
            shareUrl = shareUrlFor(session.token),
            lat = location?.lat,
            lng = location?.lng,
            updatedAtMs = location?.updatedAt?.toEpochMilli(),
            ttlSeconds = ttlSeconds,
            viewerActive = store.isWatched(session.token),
        )
    }

    private fun shareUrlFor(token: String): String = "${baseUrl.trimEnd('/')}/share/$token"

    fun updateLocation(token: String, ownerSecret: String?, report: LocationReport): ShareLocationUpdated {
        val session: ShareSession = store.findByToken(token) ?: throw ShareSessionNotFoundException()
        requireOwner(session, ownerSecret)
        val now: Instant = clock.instant()
        val location: StoredLocation = StoredLocation(
            lat = report.lat,
            lng = report.lng,
            updatedAt = now,
            remainingM = report.remainingM,
            remainingS = report.remainingS,
        )
        store.save(session.withLocation(location))
        return ShareLocationUpdated(updatedAt = now, expiresAt = session.expiresAt)
    }

    fun saveRoute(token: String, ownerSecret: String?, route: StoredRoute): ShareLocationUpdated {
        val session: ShareSession = store.findByToken(token) ?: throw ShareSessionNotFoundException()
        requireOwner(session, ownerSecret)
        val now: Instant = clock.instant()
        store.save(session.withRoute(route))
        return ShareLocationUpdated(updatedAt = now, expiresAt = session.expiresAt)
    }

    fun getRoute(token: String): RouteView {
        val session: ShareSession = store.findByToken(token)
            ?: return RouteView(expired = true, route = null)
        return RouteView(expired = false, route = session.route?.toRouteContext())
    }

    fun getLocation(token: String): LocationView {
        val session: ShareSession = store.findByToken(token)
            ?: return LocationView(lat = null, lng = null, updatedAt = null, expired = true)
        store.markViewed(token)
        return locationViewOf(session)
    }

    fun getLocationForAdmin(token: String): LocationView {
        val session: ShareSession = store.findByToken(token)
            ?: return LocationView(lat = null, lng = null, updatedAt = null, expired = true)
        return locationViewOf(session)
    }

    private fun locationViewOf(session: ShareSession): LocationView {
        val overdue: Boolean = isOverdue(session, clock.instant())
        val location: StoredLocation = session.location
            ?: return LocationView(lat = null, lng = null, updatedAt = null, expired = false, overdue = overdue)
        return LocationView(
            lat = location.lat,
            lng = location.lng,
            updatedAt = location.updatedAt,
            expired = false,
            remainingM = location.remainingM,
            remainingS = location.remainingS,
            overdue = overdue,
        )
    }

    private fun isOverdue(session: ShareSession, now: Instant): Boolean {
        val expected: Instant = session.expectedArrivalAt ?: return false
        return now.isAfter(expected.plusSeconds(MISSED_ARRIVAL_OVERDUE_SECONDS))
    }

    fun storeBackendLabel(): String = store.backend

    fun end(token: String, ownerSecret: String?) {
        val session: ShareSession = store.findByToken(token) ?: return
        requireOwner(session, ownerSecret)
        store.deleteByToken(token)
    }

    fun getWatchingStatus(token: String, ownerSecret: String?): ShareWatchStatus {
        val session: ShareSession = store.findByToken(token)
            ?: return ShareWatchStatus(watching = false, expired = true)
        requireOwner(session, ownerSecret)
        val location: StoredLocation? = session.location
        return ShareWatchStatus(
            watching = store.isWatched(token),
            lat = location?.lat,
            lng = location?.lng,
            updatedAt = location?.updatedAt,
            expired = false,
            remainingM = location?.remainingM,
            remainingS = location?.remainingS,
        )
    }

    /** 쓰기 비밀 검증 — 상수 시간 비교([MessageDigest.isEqual])로 타이밍 누출을 피하고, 누락/불일치는 404(불투명 응답). */
    private fun requireOwner(session: ShareSession, providedSecret: String?) {
        val provided: String = providedSecret ?: throw ShareSessionNotFoundException()
        val matches: Boolean = MessageDigest.isEqual(
            provided.toByteArray(Charsets.UTF_8),
            session.ownerSecret.toByteArray(Charsets.UTF_8),
        )
        if (!matches) {
            throw ShareSessionNotFoundException()
        }
    }

    private fun newToken(): String {
        val bytes: ByteArray = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return tokenEncoder.encodeToString(bytes)
    }

    private companion object {
        const val TOKEN_BYTES: Int = 16
        const val SECONDS_PER_HOUR: Long = 3600L

        val ACTIVE_SHARE_ORDER: Comparator<ActiveShareView> =
            compareByDescending<ActiveShareView> { it.updatedAtMs != null }
                .thenByDescending { it.updatedAtMs ?: 0L }
                .thenBy { it.token }
    }
}
