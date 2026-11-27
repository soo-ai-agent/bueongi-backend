package io.soo.bueongi.storage.db.share.store

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredLocation
import io.soo.bueongi.storage.db.share.model.StoredRoute
import io.soo.bueongi.storage.db.share.store.ShareSessionStore.Companion.WATCHED_FOR_SECONDS
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
            val timeLeft: Duration = timeLeftUntil(session.expiresAt)
            saveShare(session, timeLeft)
            saveLocation(session, timeLeft)
            saveRoute(session, timeLeft)
        }
    }

    private fun saveShare(session: ShareSession, timeLeft: Duration) {
        val fields: HashMap<String, String> = HashMap()
        fields[FIELD_CREATED_AT] = session.createdAt.toString()
        fields[FIELD_EXPIRES_AT] = session.expiresAt.toString()
        fields[FIELD_OWNER_SECRET] = session.ownerSecret
        val expectedArrivalAt: Instant? = session.expectedArrivalAt
        if (expectedArrivalAt != null) {
            fields[FIELD_EXPECTED_ARRIVAL_AT] = expectedArrivalAt.toString()
        }
        val key: String = shareKey(session.token)
        redis.opsForHash<String, String>().putAll(key, fields)
        redis.expire(key, timeLeft)
    }

    private fun saveLocation(session: ShareSession, timeLeft: Duration) {
        val location: StoredLocation = session.location ?: return
        val fields: HashMap<String, String> = HashMap()
        fields[FIELD_LAT] = location.lat.toString()
        fields[FIELD_LNG] = location.lng.toString()
        fields[FIELD_UPDATED_AT] = location.updatedAt.toString()
        val remainingMeters: Double? = location.remainingMeters
        if (remainingMeters != null) {
            fields[FIELD_REMAINING_METERS] = remainingMeters.toString()
        }
        val remainingSeconds: Double? = location.remainingSeconds
        if (remainingSeconds != null) {
            fields[FIELD_REMAINING_SECONDS] = remainingSeconds.toString()
        }
        val key: String = locationKey(session.token)
        redis.opsForHash<String, String>().putAll(key, fields)
        redis.expire(key, timeLeft)
    }

    private fun saveRoute(session: ShareSession, timeLeft: Duration) {
        val route: StoredRoute = session.route ?: return
        val routeJson: String = objectMapper.writeValueAsString(route)
        redis.opsForValue().set(routeKey(session.token), routeJson, timeLeft)
    }

    override fun findByToken(token: String): ShareSession? {
        return onRedis("findByToken") { read(token) }
    }

    private fun read(token: String): ShareSession? {
        val shareHash: Map<String, String> = redis.opsForHash<String, String>().entries(shareKey(token))
        if (shareHash.isEmpty()) {
            return null
        }
        val createdAtText: String = shareHash[FIELD_CREATED_AT] ?: return null
        val expiresAtText: String = shareHash[FIELD_EXPIRES_AT] ?: return null
        val ownerSecret: String = shareHash[FIELD_OWNER_SECRET] ?: return null
        val createdAt: Instant = Instant.parse(createdAtText)
        val expiresAt: Instant = Instant.parse(expiresAtText)
        var expectedArrivalAt: Instant? = null
        val expectedArrivalAtText: String? = shareHash[FIELD_EXPECTED_ARRIVAL_AT]
        if (expectedArrivalAtText != null) {
            expectedArrivalAt = toInstantOrNull(expectedArrivalAtText)
        }

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
        val updatedAtText: String = hash[FIELD_UPDATED_AT] ?: return null
        val updatedAt: Instant = toInstantOrNull(updatedAtText) ?: return null
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

    private fun toInstantOrNull(value: String): Instant? {
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

    override fun markWatched(token: String) {
        onRedis("markWatched") {
            redis.opsForValue().set(viewerKey(token), "1", Duration.ofSeconds(WATCHED_FOR_SECONDS))
        }
    }

    override fun isWatched(token: String): Boolean {
        return onRedis("isWatched") { redis.hasKey(viewerKey(token)) == true }
    }

    override fun countActive(): Int {
        return findSessionKeys().size
    }

    override fun findActive(): List<ShareSession> {
        val sessions: ArrayList<ShareSession> = ArrayList()
        for (sessionKey in findSessionKeys()) {
            val token: String = sessionKey.removePrefix(SESSION_KEY_PREFIX)
            val session: ShareSession? = findByToken(token)
            if (session != null) {
                sessions.add(session)
            }
        }
        return sessions
    }

    // KEYS 명령은 끝날 때까지 Redis 를 멈춰 세우므로 쓰지 않는다. SCAN 으로 조금씩 훑는다.
    private fun findSessionKeys(): List<String> {
        return onRedis("scanSessions") {
            val keys: ArrayList<String> = ArrayList()
            val scanOptions: ScanOptions = ScanOptions.scanOptions().match(SESSION_KEY_PATTERN).count(SCAN_BATCH).build()
            redis.scan(scanOptions).use { cursor ->
                while (cursor.hasNext()) {
                    keys.add(cursor.next())
                }
            }
            keys
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

    // Redis 는 0 이하의 남은 시간을 받지 않는다. 이미 지난 공유는 1초 뒤에 지워지게 한다.
    private fun timeLeftUntil(expiresAt: Instant): Duration {
        val timeLeft: Duration = Duration.between(clock.instant(), expiresAt)
        if (timeLeft.isNegative || timeLeft.isZero) {
            return Duration.ofSeconds(1)
        }
        return timeLeft
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
