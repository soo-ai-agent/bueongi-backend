package io.soo.bueongi.storage.db.core.share

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
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
 *
 * 런타임에 Redis 가 죽으면 모든 연산이 Spring Data `DataAccessException` 을 던진다. [onRedis] 가 이를
 * [LocationStoreUnavailableException] 으로 변환해 "없음"(null)과 "인프라 장애"를 분리한다 — 상위(API)는
 * 만료/미존재는 404, 장애는 503 으로 다르게 응답한다(불명확한 500 로 뭉뚱그리지 않는다).
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

    private val log: Logger = LoggerFactory.getLogger(RedisLocationStore::class.java)

    override val backend: String = "redis"

    override fun save(session: ShareSession) {
        onRedis("save") {
            val ttl: Duration = ttlFrom(session.expiresAt)
            val shareKey: String = shareKey(session.token)
            val shareFields: Map<String, String> = buildMap {
                put(FIELD_CREATED_AT, session.createdAt.toString())
                put(FIELD_EXPIRES_AT, session.expiresAt.toString())
                // 쓰기 비밀은 share 메타에만 보관한다 — 보호자 조회(보호자 조회 응답)엔 절대 싣지 않는다.
                put(FIELD_OWNER_SECRET, session.ownerSecret)
                session.expectedArrivalAt?.let { put(FIELD_EXPECTED_ARRIVAL_AT, it.toString()) }
            }
            redis.opsForHash<String, String>().putAll(shareKey, shareFields)
            redis.expire(shareKey, ttl)

            session.location?.let { loc ->
                val locKey: String = locationKey(session.token)
                val fields: Map<String, String> = buildMap {
                    put(FIELD_LAT, loc.lat.toString())
                    put(FIELD_LNG, loc.lng.toString())
                    put(FIELD_UPDATED_AT, loc.updatedAt.toString())
                    loc.remainingM?.let { put(FIELD_REMAINING_M, it.toString()) }
                    loc.remainingS?.let { put(FIELD_REMAINING_S, it.toString()) }
                }
                redis.opsForHash<String, String>().putAll(locKey, fields)
                redis.expire(locKey, ttl)
            }

            session.route?.let { route ->
                val routeKey: String = routeKey(session.token)
                redis.opsForValue().set(routeKey, objectMapper.writeValueAsString(route), ttl)
            }
        }
    }

    override fun findByToken(token: String): ShareSession? = onRedis("findByToken") {
        val shareHash: Map<String, String> = redis.opsForHash<String, String>().entries(shareKey(token))
        if (shareHash.isEmpty()) {
            return@onRedis null
        }
        val createdAt: Instant = shareHash[FIELD_CREATED_AT]?.let(Instant::parse) ?: return@onRedis null
        val expiresAt: Instant = shareHash[FIELD_EXPIRES_AT]?.let(Instant::parse) ?: return@onRedis null
        val ownerSecret: String = shareHash[FIELD_OWNER_SECRET] ?: return@onRedis null // 비밀 없는 손상 데이터는 미존재 취급.
        val expectedArrivalAt: Instant? = shareHash[FIELD_EXPECTED_ARRIVAL_AT]?.let(::parseInstantOrNull)

        val locHash: Map<String, String> = redis.opsForHash<String, String>().entries(locationKey(token))
        val location: StoredLocation? = if (locHash.isEmpty()) {
            null
        } else {
            val lat: Double? = locHash[FIELD_LAT]?.toDoubleOrNull()
            val lng: Double? = locHash[FIELD_LNG]?.toDoubleOrNull()
            val updatedAt: Instant? = locHash[FIELD_UPDATED_AT]?.let(Instant::parse)
            if (lat != null && lng != null && updatedAt != null) {
                StoredLocation(
                    lat = lat,
                    lng = lng,
                    updatedAt = updatedAt,
                    remainingM = locHash[FIELD_REMAINING_M]?.toDoubleOrNull(),
                    remainingS = locHash[FIELD_REMAINING_S]?.toDoubleOrNull(),
                )
            } else {
                null
            }
        }

        val route: StoredRoute? = redis.opsForValue().get(routeKey(token))?.let { json ->
            try {
                objectMapper.readValue<StoredRoute>(json)
            } catch (e: Exception) {
                log.warn("공유 경로 JSON 역직렬화 실패(경로 표시 생략): {}", e.message)
                null
            }
        }
        ShareSession(
            token = token,
            ownerSecret = ownerSecret,
            createdAt = createdAt,
            expiresAt = expiresAt,
            location = location,
            route = route,
            expectedArrivalAt = expectedArrivalAt,
        )
    }

    /** Instant 관용 파싱 — 손상값이면 null(선택 필드가 세션 전체를 깨뜨리지 않게 한다). */
    private fun parseInstantOrNull(value: String): Instant? =
        try {
            Instant.parse(value)
        } catch (e: Exception) {
            log.warn("공유 세션 시각 필드 파싱 실패(무시): {}", e.message)
            null
        }

    override fun deleteByToken(token: String) {
        onRedis("deleteByToken") {
            redis.delete(listOf(shareKey(token), locationKey(token), routeKey(token), viewerKey(token)))
        }
    }

    override fun markViewed(token: String) {
        onRedis("markViewed") {
            redis.opsForValue().set(viewerKey(token), "1", Duration.ofSeconds(VIEWER_WINDOW_SECONDS))
        }
    }

    override fun isWatched(token: String): Boolean = onRedis("isWatched") {
        redis.hasKey(viewerKey(token)) == true
    }

    override fun countActiveSessions(): Int {
        // KEYS 금지 — SCAN 으로 비차단 순회하며 세션 키(share:{token})만 센다. 만료 키는 native TTL 로 이미 없다.
        var count: Int = 0
        forEachSessionKey { count++ }
        return count
    }

    override fun findActiveSessions(): List<ShareSession> {
        // 카운트와 같은 SCAN 순회를 재사용하되, 키에서 토큰을 뽑아 세션을 복원한다.
        val sessions: MutableList<ShareSession> = mutableListOf<ShareSession>()
        forEachSessionKey { sessionKey ->
            val token: String = sessionKey.removePrefix(SESSION_KEY_PREFIX)
            findByToken(token)?.let { sessions.add(it) }
        }
        return sessions
    }

    /** 세션 키(`share:{token}`)를 SCAN 으로 비차단 순회한다(KEYS 금지). 커서는 [use] 로 항상 닫는다. */
    private fun forEachSessionKey(action: (String) -> Unit) {
        onRedis("scanSessions") {
            redis.scan(ScanOptions.scanOptions().match(SESSION_KEY_PATTERN).count(SCAN_BATCH).build())
                .use { cursor ->
                    while (cursor.hasNext()) {
                        action(cursor.next())
                    }
                }
        }
    }

    /**
     * Redis I/O 를 실행하고, 연결 실패·타임아웃(Spring Data [DataAccessException])을
     * [LocationStoreUnavailableException] 으로 변환한다. "없음"(빈 결과 → null)과 "장애"(예외)를 분리해,
     * 상위(API)가 만료/미존재(404)와 인프라 장애(503)를 다르게 응답하게 한다. token 은 로깅하지 않는다(PII·추적 방지).
     */
    private fun <T> onRedis(operation: String, block: () -> T): T =
        try {
            block()
        } catch (e: DataAccessException) {
            log.warn("Redis 접근 실패(operation={}): {}", operation, e.javaClass.simpleName)
            throw LocationStoreUnavailableException(operation, e)
        }

    /** 상한 만료까지 남은 TTL. 최소 1초 보장(만료 직전 0초/음수로 키가 즉시 삭제되는 경계 회피). */
    private fun ttlFrom(expiresAt: Instant): Duration {
        val remaining: Duration = Duration.between(clock.instant(), expiresAt)
        return if (remaining.isNegative || remaining.isZero) {
            Duration.ofSeconds(1)
        } else {
            remaining
        }
    }

    private companion object {
        /** 세션 키 접두어(`share:{token}`). SCAN 패턴·토큰 추출·[shareKey] 가 한 상수를 공유해 드리프트를 막는다. */
        const val SESSION_KEY_PREFIX: String = "share:"

        fun shareKey(token: String) = "$SESSION_KEY_PREFIX$token"
        fun locationKey(token: String) = "location:$token"
        fun routeKey(token: String) = "route:$token"
        fun viewerKey(token: String) = "viewer:$token"

        /** SCAN 매칭 패턴 — 세션 키만(location/route/viewer 제외). token 은 콜론 없는 불투명 문자열이라 `share:*` 로 정확히 걸린다. */
        const val SESSION_KEY_PATTERN: String = "$SESSION_KEY_PREFIX*"

        /** SCAN 배치 힌트(라운드트립당 반환 힌트) — 서버 부하와 왕복 횟수의 균형. */
        const val SCAN_BATCH: Long = 200L

        const val FIELD_CREATED_AT: String = "created_at"
        const val FIELD_EXPIRES_AT: String = "expires_at"
        const val FIELD_OWNER_SECRET: String = "owner_secret"
        const val FIELD_EXPECTED_ARRIVAL_AT: String = "expected_arrival_at"
        const val FIELD_LAT: String = "lat"
        const val FIELD_LNG: String = "lng"
        const val FIELD_UPDATED_AT: String = "updated_at"
        const val FIELD_REMAINING_M: String = "remaining_m"
        const val FIELD_REMAINING_S: String = "remaining_s"
    }
}
