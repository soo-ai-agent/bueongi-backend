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
        return ShareSession(token = token, createdAt = createdAt, expiresAt = expiresAt, location = location)
    }

    /** 상한 만료까지 남은 TTL. 최소 1초 보장(만료 직전 0초/음수로 키가 즉시 삭제되는 경계 회피). */
    private fun ttlFrom(expiresAt: Instant): Duration {
        val remaining = Duration.between(clock.instant(), expiresAt)
        return if (remaining.isNegative || remaining.isZero) Duration.ofSeconds(1) else remaining
    }

    private companion object {
        fun shareKey(token: String) = "share:$token"
        fun locationKey(token: String) = "location:$token"

        const val FIELD_CREATED_AT = "created_at"
        const val FIELD_EXPIRES_AT = "expires_at"
        const val FIELD_LAT = "lat"
        const val FIELD_LNG = "lng"
        const val FIELD_UPDATED_AT = "updated_at"
    }
}
