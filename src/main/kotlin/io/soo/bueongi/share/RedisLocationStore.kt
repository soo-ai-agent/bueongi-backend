package io.soo.bueongi.share

import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Redis 기반 공유 저장소. `BUEONGI_REDIS_URL` 주입 시에만 [ShareStoreConfig] 가 이 빈을 등록하고, 미주입이면
 * [InMemoryLocationStore] 가 폴백한다(동일 [LocationStore] 계약).
 *
 * Redis 키 모델(MVP 계약):
 * - `share:{token}` HASH `{ created_at, expires_at }` — 공유 메타. EXPIRE = 상한 TTL(1~3h).
 * - `location:{token}` HASH `{ lat, lng, updated_at }` — 마지막 위치. 동일 상한 TTL 로 함께 만료된다.
 *
 * 상한 만료는 Redis native TTL 이 강제한다 — [find] 는 `share:{token}` 키 존재 여부만 보면 되고(키가 없으면
 * 만료/미존재), 별도 시각 비교가 필요 없다(인메모리 폴백의 lazy 비교와 동치 결과).
 */
class RedisLocationStore(
    private val redis: StringRedisTemplate,
    private val clock: Clock,
) : LocationStore {

    override val backend: String = "redis"

    override fun save(session: ShareSession) {
        val ttl = ttlFrom(session.expiresAt)
        val shareKey = shareKey(session.token)
        redis.opsForHash<String, String>().putAll(
            shareKey,
            mapOf(
                FIELD_CREATED_AT to session.createdAt.toString(),
                FIELD_EXPIRES_AT to session.expiresAt.toString(),
                // 쓰기 비밀은 share 메타에만 보관한다 — 보호자 조회(LocationView)엔 절대 싣지 않는다.
                FIELD_OWNER_SECRET to session.ownerSecret,
            ),
        )
        redis.expire(shareKey, ttl)

        session.location?.let { loc ->
            val locKey = locationKey(session.token)
            redis.opsForHash<String, String>().putAll(
                locKey,
                mapOf(
                    FIELD_LAT to loc.lat.toString(),
                    FIELD_LNG to loc.lng.toString(),
                    FIELD_UPDATED_AT to loc.updatedAt.toString(),
                ),
            )
            // 위치 키도 공유 상한과 동일 TTL — 공유가 만료되면 위치도 함께 사라진다(잔존 추적 금지).
            redis.expire(locKey, ttl)
        }
    }

    override fun find(token: String): ShareSession? {
        val shareHash = redis.opsForHash<String, String>().entries(shareKey(token))
        if (shareHash.isEmpty()) return null // 키 없음/만료(Redis TTL 이 이미 삭제) → 미존재.
        val createdAt = shareHash[FIELD_CREATED_AT]?.let(Instant::parse) ?: return null
        val expiresAt = shareHash[FIELD_EXPIRES_AT]?.let(Instant::parse) ?: return null
        val ownerSecret = shareHash[FIELD_OWNER_SECRET] ?: return null // 비밀 없는 손상 데이터는 미존재 취급.

        val locHash = redis.opsForHash<String, String>().entries(locationKey(token))
        val location = if (locHash.isEmpty()) {
            null
        } else {
            val lat = locHash[FIELD_LAT]?.toDoubleOrNull()
            val lng = locHash[FIELD_LNG]?.toDoubleOrNull()
            val updatedAt = locHash[FIELD_UPDATED_AT]?.let(Instant::parse)
            if (lat != null && lng != null && updatedAt != null) {
                StoredLocation(lat = lat, lng = lng, updatedAt = updatedAt)
            } else {
                null
            }
        }
        return ShareSession(
            token = token,
            ownerSecret = ownerSecret,
            createdAt = createdAt,
            expiresAt = expiresAt,
            location = location,
        )
    }

    override fun delete(token: String) {
        // 공유/위치/시청 키를 함께 삭제 → 즉시 종료(만료 전에 사용자가 도착/중단). 없는 키 삭제는 무해(멱등).
        redis.delete(listOf(shareKey(token), locationKey(token), viewerKey(token)))
    }

    override fun markViewed(token: String) {
        // 보호자 조회 시각을 짧은 TTL 키로 기록 — 폴링이 끊기면(창 닫힘) TTL 로 자동 소멸(시청 종료).
        redis.opsForValue().set(viewerKey(token), "1", Duration.ofSeconds(VIEWER_WINDOW_SECONDS))
    }

    override fun isWatched(token: String): Boolean = redis.hasKey(viewerKey(token)) == true

    /** 상한 만료까지 남은 TTL. 최소 1초 보장(만료 직전 0초/음수로 키가 즉시 삭제되는 경계 회피). */
    private fun ttlFrom(expiresAt: Instant): Duration {
        val remaining = Duration.between(clock.instant(), expiresAt)
        return if (remaining.isNegative || remaining.isZero) Duration.ofSeconds(1) else remaining
    }

    private companion object {
        /** 보호자 조회 후 이 시간(초) 동안 '시청 중'으로 본다 — 보호자 폴링(5초)의 약 2~3배 여유. */
        const val VIEWER_WINDOW_SECONDS = 12L

        fun shareKey(token: String) = "share:$token"
        fun locationKey(token: String) = "location:$token"
        fun viewerKey(token: String) = "viewer:$token"

        const val FIELD_CREATED_AT = "created_at"
        const val FIELD_EXPIRES_AT = "expires_at"
        const val FIELD_OWNER_SECRET = "owner_secret"
        const val FIELD_LAT = "lat"
        const val FIELD_LNG = "lng"
        const val FIELD_UPDATED_AT = "updated_at"
    }
}
