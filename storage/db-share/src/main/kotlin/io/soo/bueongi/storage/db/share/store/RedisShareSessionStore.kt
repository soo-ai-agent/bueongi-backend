package io.soo.bueongi.storage.db.share.store

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredLocation
import io.soo.bueongi.storage.db.share.model.StoredRoute
import io.soo.bueongi.storage.db.share.store.ShareSessionStore.Companion.VIEWER_WINDOW_SECONDS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

// 저장 계약: `share:/location:/route:/viewer:{token}` 키와 snake_case HASH 필드 이름을 바꾸면, 배포 전에 저장된 공유(최대 3시간)를 위해 옛 이름도 읽는다.
class RedisShareSessionStore(
    private val redis: StringRedisTemplate,
    private val clock: Clock,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : ShareSessionStore {
    private val log: Logger = LoggerFactory.getLogger(RedisShareSessionStore::class.java)

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
                    val expectedArrivalAt: Instant? = session.expectedArrivalAt
                    if (expectedArrivalAt != null) {
                        put(FIELD_EXPECTED_ARRIVAL_AT, expectedArrivalAt.toString())
                    }
                }
            redis.opsForHash<String, String>().putAll(shareKey, shareFields)
            redis.expire(shareKey, ttl)

            val location: StoredLocation? = session.location
            if (location != null) {
                val locKey: String = locationKey(session.token)
                val fields: Map<String, String> =
                    buildMap {
                        put(FIELD_LAT, location.lat.toString())
                        put(FIELD_LNG, location.lng.toString())
                        put(FIELD_UPDATED_AT, location.updatedAt.toString())
                        val remainingMeters: Double? = location.remainingMeters
                        if (remainingMeters != null) {
                            put(FIELD_REMAINING_METERS, remainingMeters.toString())
                        }
                        val remainingSeconds: Double? = location.remainingSeconds
                        if (remainingSeconds != null) {
                            put(FIELD_REMAINING_SECONDS, remainingSeconds.toString())
                        }
                    }
                redis.opsForHash<String, String>().putAll(locKey, fields)
                redis.expire(locKey, ttl)
            }

            val route: StoredRoute? = session.route
            if (route != null) {
                val routeKey: String = routeKey(session.token)
                redis.opsForValue().set(routeKey, objectMapper.writeValueAsString(route), ttl)
            }
        }
    }

    override fun findByToken(token: String): ShareSession? {
        return onRedis("findByToken") { read(token) }
    }

    private fun read(token: String): ShareSession? {
        val shareHash: Map<String, String> = redis.opsForHash<String, String>().entries(shareKey(token))
        if (shareHash.isEmpty()) {
            return null
        }
        val createdAt: Instant = shareHash[FIELD_CREATED_AT]?.let(Instant::parse) ?: return null
        val expiresAt: Instant = shareHash[FIELD_EXPIRES_AT]?.let(Instant::parse) ?: return null
        val ownerSecret: String = shareHash[FIELD_OWNER_SECRET] ?: return null
        val expectedArrivalAt: Instant? = shareHash[FIELD_EXPECTED_ARRIVAL_AT]?.let(::parseInstantOrNull)

        return ShareSession(
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
        // 배포 전에 옛 이름으로 저장된 위치(공유 수명 최대 3시간)도 남은 거리·시간을 잃지 않게 remaining_m·remaining_s 도 읽는다.
        val remainingMeters: String? = hash[FIELD_REMAINING_METERS] ?: hash["remaining_m"]
        val remainingSeconds: String? = hash[FIELD_REMAINING_SECONDS] ?: hash["remaining_s"]
        return StoredLocation(
            lat = lat,
            lng = lng,
            updatedAt = updatedAt,
            remainingMeters = remainingMeters?.toDoubleOrNull(),
            remainingSeconds = remainingSeconds?.toDoubleOrNull(),
        )
    }

    // 경로 JSON 이 깨져도 공유 자체는 살린다 — 보호자에게 위치는 계속 보여야 한다.
    private fun readRoute(token: String): StoredRoute? {
        val json: String = redis.opsForValue().get(routeKey(token)) ?: return null
        return try {
            objectMapper.readValue<StoredRoute>(json)
        } catch (e: JsonProcessingException) {
            log.warn("공유 경로 JSON 역직렬화 실패(경로 표시 생략) | 원인={}", e.javaClass.simpleName)
            null
        }
    }

    private fun parseInstantOrNull(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            log.warn("공유 세션 시각 필드 파싱 실패(무시) | 원인={}", e.javaClass.simpleName)
            null
        }
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

    override fun isWatched(token: String): Boolean {
        return onRedis("isWatched") { redis.hasKey(viewerKey(token)) == true }
    }

    override fun countActive(): Int {
        var count: Int = 0
        forEachSessionKey { count++ }
        return count
    }

    override fun findActive(): List<ShareSession> {
        val sessions: ArrayList<ShareSession> = ArrayList()
        forEachSessionKey { sessionKey ->
            val token: String = sessionKey.removePrefix(SESSION_KEY_PREFIX)
            val session: ShareSession? = findByToken(token)
            if (session != null) {
                sessions.add(session)
            }
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
    private fun <T> onRedis(operation: String, block: () -> T): T {
        try {
            return block()
        } catch (e: DataAccessException) {
            log.warn("Redis 접근 실패(operation={}): {}", operation, e.javaClass.simpleName)
            throw LocationStoreUnavailableException(operation, e)
        }
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

        fun shareKey(token: String): String {
            return "$SESSION_KEY_PREFIX$token"
        }

        fun locationKey(token: String): String {
            return "location:$token"
        }

        fun routeKey(token: String): String {
            return "route:$token"
        }

        fun viewerKey(token: String): String {
            return "viewer:$token"
        }

        const val SESSION_KEY_PATTERN: String = "$SESSION_KEY_PREFIX*"

        const val SCAN_BATCH: Long = 200L

        const val FIELD_CREATED_AT: String = "created_at"
        const val FIELD_EXPIRES_AT: String = "expires_at"
        const val FIELD_OWNER_SECRET: String = "owner_secret"
        const val FIELD_EXPECTED_ARRIVAL_AT: String = "expected_arrival_at"
        const val FIELD_LAT: String = "lat"
        const val FIELD_LNG: String = "lng"
        const val FIELD_UPDATED_AT: String = "updated_at"
        const val FIELD_REMAINING_METERS: String = "remaining_meters"
        const val FIELD_REMAINING_SECONDS: String = "remaining_seconds"
    }
}
