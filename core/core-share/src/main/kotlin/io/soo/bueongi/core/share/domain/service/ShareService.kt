package io.soo.bueongi.core.share.domain.service

import io.soo.bueongi.core.enums.ShareProgressStatus
import io.soo.bueongi.core.share.api.response.ActiveShareView
import io.soo.bueongi.core.share.api.response.CreateShareResponse
import io.soo.bueongi.core.share.api.response.LocationView
import io.soo.bueongi.core.share.api.response.RouteView
import io.soo.bueongi.core.share.api.response.ShareSessionsResponse
import io.soo.bueongi.core.share.api.response.ShareStatusResponse
import io.soo.bueongi.core.share.api.response.UpdateLocationResponse
import io.soo.bueongi.core.share.api.response.WatchResponse
import io.soo.bueongi.core.share.domain.error.ShareSessionNotFoundException
import io.soo.bueongi.core.share.domain.model.DEFAULT_EXPIRES_HOURS
import io.soo.bueongi.core.share.domain.model.LocationReport
import io.soo.bueongi.core.share.domain.model.MISSED_ARRIVAL_GRACE_SECONDS
import io.soo.bueongi.core.share.domain.model.progressStatus
import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredLocation
import io.soo.bueongi.storage.db.share.model.StoredRoute
import io.soo.bueongi.storage.db.share.store.ShareSessionStore
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
    private val store: ShareSessionStore,
    private val clock: Clock,
    @param:Value("\${bueongi.share.base-url}") private val baseUrl: String,
) {
    private val random: SecureRandom = SecureRandom()
    private val tokenEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun create(expiresInHours: Int?, etaSeconds: Int? = null): CreateShareResponse {
        val hours: Int = expiresInHours ?: DEFAULT_EXPIRES_HOURS
        val token: String = newToken()
        val ownerSecret: String = newToken()
        val now: Instant = clock.instant()
        val expiresAt: Instant = now.plusSeconds(hours.toLong() * SECONDS_PER_HOUR)
        var expectedArrivalAt: Instant? = null
        if (etaSeconds != null) {
            expectedArrivalAt = now.plusSeconds(etaSeconds.toLong() + MISSED_ARRIVAL_GRACE_SECONDS)
        }
        val session: ShareSession =
            ShareSession(
                token = token,
                ownerSecret = ownerSecret,
                createdAt = now,
                expiresAt = expiresAt,
                location = null,
                expectedArrivalAt = expectedArrivalAt,
            )
        store.save(session)
        val shareUrl: String = urlFor(token)
        return CreateShareResponse.from(session, shareUrl)
    }

    fun listActive(): ShareSessionsResponse {
        val now: Instant = clock.instant()
        val views: ArrayList<ActiveShareView> = ArrayList()
        for (session in store.findActive()) {
            views.add(toActiveShareView(session, now))
        }
        views.sortWith(ACTIVE_SHARE_ORDER)
        return ShareSessionsResponse.from(views)
    }

    fun getStatus(): ShareStatusResponse {
        val activeSessions: Int = store.countActive()
        return ShareStatusResponse.from(activeSessions)
    }

    private fun toActiveShareView(session: ShareSession, now: Instant): ActiveShareView {
        val ttlSeconds: Long = Duration.between(now, session.expiresAt).seconds.coerceAtLeast(0)
        val shareUrl: String = urlFor(session.token)
        val viewerActive: Boolean = store.isWatched(session.token)
        return ActiveShareView.from(session, shareUrl, ttlSeconds, viewerActive)
    }

    private fun urlFor(token: String): String {
        return "${baseUrl.trimEnd('/')}/share/$token"
    }

    fun updateLocation(token: String, ownerSecret: String?, report: LocationReport): UpdateLocationResponse {
        val session: ShareSession = store.findByToken(token) ?: throw ShareSessionNotFoundException()
        requireOwner(session, ownerSecret)
        val now: Instant = clock.instant()
        val location: StoredLocation =
            StoredLocation(
                lat = report.lat,
                lng = report.lng,
                updatedAt = now,
                remainingMeters = report.remainingMeters,
                remainingSeconds = report.remainingSeconds,
            )
        store.save(session.withLocation(location))
        return UpdateLocationResponse.from(session, now)
    }

    fun saveRoute(token: String, ownerSecret: String?, route: StoredRoute): UpdateLocationResponse {
        val session: ShareSession = store.findByToken(token) ?: throw ShareSessionNotFoundException()
        requireOwner(session, ownerSecret)
        val now: Instant = clock.instant()
        store.save(session.withRoute(route))
        return UpdateLocationResponse.from(session, now)
    }

    fun getRoute(token: String): RouteView {
        val session: ShareSession =
            store.findByToken(token)
                ?: return RouteView.EXPIRED
        return RouteView.from(session.route)
    }

    fun getLocation(token: String): LocationView {
        val session: ShareSession =
            store.findByToken(token)
                ?: return LocationView.EXPIRED
        store.markViewed(token)
        return locationViewOf(session)
    }

    fun getLocationForAdmin(token: String): LocationView {
        val session: ShareSession =
            store.findByToken(token)
                ?: return LocationView.EXPIRED
        return locationViewOf(session)
    }

    private fun locationViewOf(session: ShareSession): LocationView {
        val status: ShareProgressStatus = session.progressStatus(clock.instant())
        val location: StoredLocation =
            session.location
                ?: return LocationView.waiting(status)
        return LocationView.from(location, status)
    }

    fun storeBackendLabel(): String {
        return store.backend
    }

    fun end(token: String, ownerSecret: String?) {
        val session: ShareSession = store.findByToken(token) ?: return
        requireOwner(session, ownerSecret)
        store.deleteByToken(token)
    }

    fun getWatchingStatus(token: String, ownerSecret: String?): WatchResponse {
        val session: ShareSession =
            store.findByToken(token)
                ?: return WatchResponse.EXPIRED
        requireOwner(session, ownerSecret)
        val watching: Boolean = store.isWatched(token)
        return WatchResponse.from(watching, session.location)
    }

    // 상수 시간 비교로 타이밍 누출을 피하고, 누락/불일치는 404(불투명 응답).
    private fun requireOwner(session: ShareSession, providedSecret: String?) {
        val provided: String = providedSecret ?: throw ShareSessionNotFoundException()
        val matches: Boolean =
            MessageDigest.isEqual(
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
