package io.soo.bueongi.share

import io.soo.bueongi.common.ShareSessionNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

/**
 * 위치 공유 생명주기(생성 / 위치 갱신 / 조회) + TTL 로직. 저장은 [LocationStore](Redis/인메모리)에 위임한다.
 *
 * 안심앱 프라이버시 원칙을 코드로 강제한다:
 * - token 은 추측 불가능한 128-bit URL-safe 랜덤(링크 공유 모델, PII 미저장).
 * - 공유는 1~3시간 상한 TTL 로 자동 만료(무기한 추적 금지) — Redis native TTL 또는 인메모리 lazy 비교.
 * - 모든 시각은 주입된 [clock] 기준이라 만료 거동을 결정론적으로 테스트한다.
 */
@Service
class ShareService(
    private val store: LocationStore,
    private val clock: Clock,
    @Value("\${bueongi.share.base-url}") private val baseUrl: String,
) {
    private val random = SecureRandom()
    private val tokenEncoder = Base64.getUrlEncoder().withoutPadding()

    /** 공유 생성 → token·share_url·상한 만료 반환. expires_in_hours 미지정이면 [DEFAULT_EXPIRES_HOURS]. */
    fun create(request: CreateShareRequest?): CreateShareResponse {
        val hours = request?.expiresInHours ?: DEFAULT_EXPIRES_HOURS
        val token = newToken()
        val now = clock.instant()
        val expiresAt = now.plusSeconds(hours.toLong() * SECONDS_PER_HOUR)
        store.save(ShareSession(token = token, createdAt = now, expiresAt = expiresAt, location = null))
        return CreateShareResponse(
            token = token,
            shareUrl = "${baseUrl.trimEnd('/')}/share/$token",
            expiresAt = expiresAt,
        )
    }

    /**
     * 위치 갱신(사용자 단말, 5초마다). 토큰 없음/만료면 [ShareSessionNotFoundException]→404.
     * (좌표 null 은 @Valid 가 이미 400 으로 거른 뒤이므로 여기선 non-null 보장.)
     */
    fun updateLocation(token: String, request: UpdateLocationRequest): UpdateLocationResponse {
        val session = store.find(token) ?: throw ShareSessionNotFoundException()
        val now = clock.instant()
        val location = StoredLocation(lat = request.lat!!, lng = request.lng!!, updatedAt = now)
        store.save(session.copy(location = location))
        return UpdateLocationResponse(updatedAt = now, expiresAt = session.expiresAt)
    }

    /**
     * 위치 조회(보호자 웹 5초 폴링). 항상 200 으로 응답하고 상태는 [LocationView] 로 표현한다:
     * 토큰 없음/만료 → `expired:true`(좌표 null), 살아있으나 첫 위치 전 → `expired:false`+좌표 null, 위치 있음 → 좌표.
     */
    fun getLocation(token: String): LocationView {
        val session = store.find(token)
            ?: return LocationView(lat = null, lng = null, updatedAt = null, expired = true)
        val location = session.location
            ?: return LocationView(lat = null, lng = null, updatedAt = null, expired = false)
        return LocationView(
            lat = location.lat,
            lng = location.lng,
            updatedAt = location.updatedAt,
            expired = false,
        )
    }

    /** 추측 불가능한 128-bit URL-safe 랜덤 토큰(22자, 패딩 없음). */
    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return tokenEncoder.encodeToString(bytes)
    }

    private companion object {
        /** token 엔트로피(바이트). 16바이트 = 128-bit. */
        const val TOKEN_BYTES = 16
        const val SECONDS_PER_HOUR = 3600L
    }
}
