package io.soo.bueongi.storage.db.core.share.store

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.soo.bueongi.storage.db.core.share.model.ShareSession
import io.soo.bueongi.storage.db.core.share.model.StoredLocation
import io.soo.bueongi.storage.db.core.share.model.StoredRoute
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant

// 저장 계약: `share:/location:/route:/viewer:{token}` 키와 snake_case HASH 필드는 리네임 금지.
class RedisLocationStore(
    private val redis: StringRedisTemplate,
    private val clock: Clock,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : LocationStore {
    private val log: Logger = LoggerFactory.getLogger(RedisLocationStore::class.java)

    override val backend: String = "redis"

    override fun save(session: ShareSession) {
        onRedis("save") {
            val ttl: Duration = ttlFrom(session.expiresAt)
            val shareKey: String = shareKey(session.token)
            val shareFields: Map<String, String> =
                buildMap {
                    put(FIELD_CREATED_AT, session.createdAt.toString())
                    put(FIELD_EXPIRES_AT, session.expiresAt.toString())
                    put(FIELD_OWNER_SECRET, session.ownerSecret)
                    session.expectedArrivalAt?.let { put(FIELD_EXPECTED_ARRIVAL_AT, it.toString()) }
                }
            redis.opsForHash<String, String>().putAll(shareKey, shareFields)
            redis.expire(shareKey, ttl)

            session.location?.let { loc ->
                val locKey: String = locationKey(session.token)
                val fields: Map<String, String> =
                    buildMap {
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

    override fun findByToken(token: String): ShareSession? =
        onRedis("findByToken") {
            val shareHash: Map<String, String> = redis.opsForHash<String, String>().entries(shareKey(token))
            if (shareHash.isEmpty()) {
                return@onRedis null
            }
            val createdAt: Instant = shareHash[FIELD_CREATED_AT]?.let(Instant::parse) ?: return@onRedis null
            val expiresAt: Instant = shareHash[FIELD_EXPIRES_AT]?.let(Instant::parse) ?: return@onRedis null
            val ownerSecret: String = shareHash[FIELD_OWNER_SECRET] ?: return@onRedis null
            val expectedArrivalAt: Instant? = shareHash[FIELD_EXPECTED_ARRIVAL_AT]?.let(::parseInstantOrNull)

            ShareSession(
                token = token,
                ownerSecret = ownerSecret,
                createdAt = createdAt,
                expiresAt = expiresAt,
                location = readLocation(token),
                route = readRoute(token),
                expectedArrivalAt = expectedArrivalAt,
            )
        }

    private fun readLocation(token: String): StoredLocation? {
        val hash: Map<String, String> = redis.opsForHash<String, String>().entries(locationKey(token))
        val lat: Double = hash[FIELD_LAT]?.toDoubleOrNull() ?: return null
        val lng: Double = hash[FIELD_LNG]?.toDoubleOrNull() ?: return null
        val updatedAt: Instant = hash[FIELD_UPDATED_AT]?.let(::parseInstantOrNull) ?: return null
        return StoredLocation(
            lat = lat,
            lng = lng,
            updatedAt = updatedAt,
            remainingM = hash[FIELD_REMAINING_M]?.toDoubleOrNull(),
            remainingS = hash[FIELD_REMAINING_S]?.toDoubleOrNull(),
        )
    }

    // 경로 JSON 이 깨져도 공유 자체는 살린다 — 보호자에게 위치는 계속 보여야 한다.
    private fun readRoute(token: String): StoredRoute? =
        redis.opsForValue().get(routeKey(token))?.let { json ->
            try {
                objectMapper.readValue<StoredRoute>(json)
            } catch (e: Exception) {
                log.warn("공유 경로 JSON 역직렬화 실패(경로 표시 생략): {}", e.message)
                null
            }
        }

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

    override fun isWatched(token: String): Boolean =
        onRedis("isWatched") {
            redis.hasKey(viewerKey(token)) == true
        }

    override fun countActiveSessions(): Int {
        var count: Int = 0
        forEachSessionKey { count++ }
        return count
    }

    override fun findActiveSessions(): List<ShareSession> {
        val sessions: MutableList<ShareSession> = mutableListOf<ShareSession>()
        forEachSessionKey { sessionKey ->
            val token: String = sessionKey.removePrefix(SESSION_KEY_PREFIX)
            findByToken(token)?.let { sessions.add(it) }
        }
        return sessions
    }

    // KEYS 금지 — 운영 Redis 를 막는다.
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

    // token 은 로깅하지 않는다(추적 방지).
    private fun <T> onRedis(operation: String, block: () -> T): T =
        try {
            block()
        } catch (e: DataAccessException) {
            log.warn("Redis 접근 실패(operation={}): {}", operation, e.javaClass.simpleName)
            throw LocationStoreUnavailableException(operation, e)
        }

    private fun ttlFrom(expiresAt: Instant): Duration {
        val remaining: Duration = Duration.between(clock.instant(), expiresAt)
        return if (remaining.isNegative || remaining.isZero) {
            Duration.ofSeconds(1)
        } else {
            remaining
        }
    }

    private companion object {
        const val SESSION_KEY_PREFIX: String = "share:"

        fun shareKey(token: String) = "$SESSION_KEY_PREFIX$token"

        fun locationKey(token: String) = "location:$token"

        fun routeKey(token: String) = "route:$token"

        fun viewerKey(token: String) = "viewer:$token"

        const val SESSION_KEY_PATTERN: String = "$SESSION_KEY_PREFIX*"

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
