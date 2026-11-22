package io.soo.bueongi.storage.db.share.store

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredRoute
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant

class JpaLocationStore(
    private val repository: ShareSessionRepository,
    private val clock: Clock,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : LocationStore {
    private val log: Logger = LoggerFactory.getLogger(JpaLocationStore::class.java)

    override val backend: String = "db"

    override fun save(session: ShareSession) {
        onDb("save") {
            val routeJson: String? = serializeRoute(session.route)
            // save 는 행 전체를 덮어쓰므로, 보호자 열람 기록(viewed_at)은 기존 행에서 꺼내 보존한다.
            val existing: ShareSessionEntity? = repository.findById(session.token).orElse(null)
            repository.save(ShareSessionEntity.from(session, routeJson, existing?.viewedAt))
        }
    }

    override fun findByToken(token: String): ShareSession? {
        return onDb("findByToken") {
            val entity: ShareSessionEntity = repository.findById(token).orElse(null) ?: return@onDb null
            if (entity.isExpired(clock.instant())) {
                return@onDb null
            }
            entity.toShareSession(entity.locationOrNull(), routeOrNull(entity.routeJson))
        }
    }

    override fun deleteByToken(token: String) {
        onDb("deleteByToken") {
            repository.deleteById(token)
        }
    }

    override fun markViewed(token: String) {
        onDb("markViewed") {
            val entity: ShareSessionEntity = repository.findById(token).orElse(null) ?: return@onDb
            entity.markViewed(clock.instant())
            repository.save(entity)
        }
    }

    override fun isWatched(token: String): Boolean {
        return onDb("isWatched") {
            val entity: ShareSessionEntity = repository.findById(token).orElse(null) ?: return@onDb false
            val viewedAt: Instant = entity.viewedAt ?: return@onDb false
            clock.instant().isBefore(viewedAt.plusSeconds(VIEWER_WINDOW_SECONDS))
        }
    }

    override fun countActiveSessions(): Int {
        return onDb("countActiveSessions") {
            repository.countByExpiresAtAfter(clock.instant()).toInt()
        }
    }

    override fun findActiveSessions(): List<ShareSession> {
        return onDb("findActiveSessions") {
            val sessions: ArrayList<ShareSession> = ArrayList()
            for (entity in repository.findByExpiresAtAfter(clock.instant())) {
                sessions.add(entity.toShareSession(entity.locationOrNull(), routeOrNull(entity.routeJson)))
            }
            sessions
        }
    }

    // Redis 는 TTL 이 지웠지만 DB 는 직접 지워야 한다 — 조회는 expires_at 로 이미 거르므로 모아서 지운다.
    @Scheduled(fixedDelay = EXPIRED_SWEEP_INTERVAL_MS)
    fun deleteExpired() {
        onDb("deleteExpired") {
            repository.deleteExpired(clock.instant())
        }
    }

    private fun serializeRoute(route: StoredRoute?): String? {
        if (route == null) {
            return null
        }
        return objectMapper.writeValueAsString(route)
    }

    // 경로 JSON 이 깨져도 공유 자체는 살린다 — 보호자에게 위치는 계속 보여야 한다.
    private fun routeOrNull(json: String?): StoredRoute? {
        if (json == null) {
            return null
        }
        return try {
            objectMapper.readValue<StoredRoute>(json)
        } catch (e: JsonProcessingException) {
            log.warn("공유 경로 JSON 역직렬화 실패(경로 표시 생략) | 원인={}", e.javaClass.simpleName)
            null
        }
    }

    // token 은 로깅하지 않는다(추적 방지).
    private fun <T> onDb(operation: String, block: () -> T): T {
        try {
            return block()
        } catch (e: DataAccessException) {
            log.warn("공유 DB 접근 실패(operation={}): {}", operation, e.javaClass.simpleName)
            throw LocationStoreUnavailableException(operation, e)
        }
    }

    private companion object {
        const val EXPIRED_SWEEP_INTERVAL_MS: Long = 600_000L
    }
}
