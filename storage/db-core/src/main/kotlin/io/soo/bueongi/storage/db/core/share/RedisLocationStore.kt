package io.soo.bueongi.storage.db.core.share

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ScanOptions
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
 * 상한 만료는 Redis native TTL 이 강제한다 — [findByToken] 은 `share:{token}` 키 존재 여부만 보면 되고(키가 없으면
 * 만료/미존재), 별도 시각 비교가 필요 없다(인메모리 폴백의 lazy 비교와 동치 결과).
 */
class RedisLocationStore(
    private val redis: StringRedisTemplate,
    private val clock: Clock,
    /**
     * 경로 컨텍스트([StoredRoute])는 리스트(경로선·마커)를 포함해 flat HASH 로 못 담으므로 JSON 문자열로 직렬화해
     * `route:{token}` 값 키에 저장한다(위치 HASH 와 별개 키, 동일 TTL). 기본값이라 기존 2-인자 배선/테스트가 그대로 동작한다.
     */
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : LocationStore {

    private val log = LoggerFactory.getLogger(RedisLocationStore::class.java)

    override val backend: String = "redis"

    override fun save(session: ShareSession) {
        val ttl = ttlFrom(session.expiresAt)
        val shareKey = shareKey(session.token)
        redis.opsForHash<String, String>().putAll(
            shareKey,
            mapOf(
                FIELD_CREATED_AT to session.createdAt.toString(),
                FIELD_EXPIRES_AT to session.expiresAt.toString(),
                // 쓰기 비밀은 share 메타에만 보관한다 — 보호자 조회(보호자 조회 응답)엔 절대 싣지 않는다.
                FIELD_OWNER_SECRET to session.ownerSecret,
            ),
        )
        redis.expire(shareKey, ttl)

        session.location?.let { loc ->
            val locKey = locationKey(session.token)
            // 잔여 거리/시간은 앱이 보고했을 때만 필드를 추가한다 — 없을 땐 HASH 에 넣지 않아 기존 {lat,lng,updated_at} 형태를 유지.
            val fields = buildMap {
                put(FIELD_LAT, loc.lat.toString())
                put(FIELD_LNG, loc.lng.toString())
                put(FIELD_UPDATED_AT, loc.updatedAt.toString())
                loc.remainingM?.let { put(FIELD_REMAINING_M, it.toString()) }
                loc.remainingS?.let { put(FIELD_REMAINING_S, it.toString()) }
            }
            redis.opsForHash<String, String>().putAll(locKey, fields)
            // 위치 키도 공유 상한과 동일 TTL — 공유가 만료되면 위치도 함께 사라진다(잔존 추적 금지).
            redis.expire(locKey, ttl)
        }

        session.route?.let { route ->
            val routeKey = routeKey(session.token)
            // 경로 컨텍스트는 JSON 문자열 값 키로 저장 — 리스트(경로선·마커) 때문에 flat HASH 로 못 담는다. 동일 상한 TTL.
            redis.opsForValue().set(routeKey, objectMapper.writeValueAsString(route), ttl)
        }
    }

    override fun findByToken(token: String): ShareSession? {
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
                StoredLocation(
                    lat = lat,
                    lng = lng,
                    updatedAt = updatedAt,
                    // 누락(구세션/미보고) 시 null — 안전한 기본값.
                    remainingM = locHash[FIELD_REMAINING_M]?.toDoubleOrNull(),
                    remainingS = locHash[FIELD_REMAINING_S]?.toDoubleOrNull(),
                )
            } else {
                null
            }
        }

        // 경로 컨텍스트: JSON 값 키. 없으면(미저장/구세션) null. 손상 JSON 은 미존재 취급(경로만 없는 것으로, 위치는 살린다).
        val route = redis.opsForValue().get(routeKey(token))?.let { json ->
            try {
                objectMapper.readValue<StoredRoute>(json)
            } catch (e: Exception) {
                // 다른 파싱 실패 지점(FacilityRepository·클라이언트)과 동일하게 경고를 남긴다 —
                // 모델 변경/데이터 손상으로 보호자 경로 표시가 조용히 사라지는 것을 관측 가능하게(token 은 비로깅).
                log.warn("공유 경로 JSON 역직렬화 실패(경로 표시 생략): {}", e.message)
                null
            }
        }
        return ShareSession(
            token = token,
            ownerSecret = ownerSecret,
            createdAt = createdAt,
            expiresAt = expiresAt,
            location = location,
            route = route,
        )
    }

    override fun deleteByToken(token: String) {
        // 공유/위치/경로/시청 키를 함께 삭제 → 즉시 종료(만료 전에 사용자가 도착/중단). 없는 키 삭제는 무해(멱등).
        redis.delete(listOf(shareKey(token), locationKey(token), routeKey(token), viewerKey(token)))
    }

    override fun markViewed(token: String) {
        // 보호자 조회 시각을 짧은 TTL 키로 기록 — 폴링이 끊기면(창 닫힘) TTL 로 자동 소멸(시청 종료).
        redis.opsForValue().set(viewerKey(token), "1", Duration.ofSeconds(VIEWER_WINDOW_SECONDS))
    }

    override fun isWatched(token: String): Boolean = redis.hasKey(viewerKey(token)) == true

    override fun countActiveSessions(): Int {
        // KEYS 금지 — SCAN 으로 비차단 순회하며 세션 키(share:{token})만 센다. 만료 키는 native TTL 로 이미 없다.
        // location/route/viewer 키는 패턴에서 제외되어 중복 카운트되지 않는다.
        var count = 0
        forEachSessionKey { count++ }
        return count
    }

    override fun findActiveSessions(): List<ShareSession> {
        // 카운트와 같은 SCAN 순회를 재사용하되, 키에서 토큰을 뽑아 세션을 복원한다.
        // findByToken 이 만료/손상 키를 null 로 걸러내므로(native TTL 과 lazy 정합) 활성 세션만 남는다.
        val sessions = mutableListOf<ShareSession>()
        forEachSessionKey { sessionKey ->
            val token = sessionKey.removePrefix(SESSION_KEY_PREFIX)
            findByToken(token)?.let { sessions.add(it) }
        }
        return sessions
    }

    /** 세션 키(`share:{token}`)를 SCAN 으로 비차단 순회한다(KEYS 금지). 커서는 [use] 로 항상 닫는다. */
    private fun forEachSessionKey(action: (String) -> Unit) {
        redis.scan(ScanOptions.scanOptions().match(SESSION_KEY_PATTERN).count(SCAN_BATCH).build())
            .use { cursor ->
                while (cursor.hasNext()) {
                    action(cursor.next())
                }
            }
    }

    /** 상한 만료까지 남은 TTL. 최소 1초 보장(만료 직전 0초/음수로 키가 즉시 삭제되는 경계 회피). */
    private fun ttlFrom(expiresAt: Instant): Duration {
        val remaining = Duration.between(clock.instant(), expiresAt)
        return if (remaining.isNegative || remaining.isZero) {
            Duration.ofSeconds(1)
        } else {
            remaining
        }
    }

    private companion object {
        /** 세션 키 접두어(`share:{token}`). SCAN 패턴·토큰 추출·[shareKey] 가 한 상수를 공유해 드리프트를 막는다. */
        const val SESSION_KEY_PREFIX = "share:"

        fun shareKey(token: String) = "$SESSION_KEY_PREFIX$token"
        fun locationKey(token: String) = "location:$token"
        fun routeKey(token: String) = "route:$token"
        fun viewerKey(token: String) = "viewer:$token"

        /** SCAN 매칭 패턴 — 세션 키만(location/route/viewer 제외). token 은 콜론 없는 불투명 문자열이라 `share:*` 로 정확히 걸린다. */
        const val SESSION_KEY_PATTERN = "$SESSION_KEY_PREFIX*"

        /** SCAN 배치 힌트(라운드트립당 반환 힌트) — 서버 부하와 왕복 횟수의 균형. */
        const val SCAN_BATCH = 200L

        const val FIELD_CREATED_AT = "created_at"
        const val FIELD_EXPIRES_AT = "expires_at"
        const val FIELD_OWNER_SECRET = "owner_secret"
        const val FIELD_LAT = "lat"
        const val FIELD_LNG = "lng"
        const val FIELD_UPDATED_AT = "updated_at"
        const val FIELD_REMAINING_M = "remaining_m"
        const val FIELD_REMAINING_S = "remaining_s"
    }
}
