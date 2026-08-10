package io.soo.bueongi.storage.db.core.share

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryLocationStore(
    private val clock: Clock,
) : LocationStore {

    private val sessions: ConcurrentHashMap<String, ShareSession> = ConcurrentHashMap<String, ShareSession>()
    private val viewers: ConcurrentHashMap<String, Instant> = ConcurrentHashMap<String, Instant>()

    override val backend: String = "memory"

    override fun save(session: ShareSession) {
        sessions[session.token] = session
    }

    override fun findByToken(token: String): ShareSession? {
        val session: ShareSession = sessions[token] ?: return null
        if (session.isExpired(clock.instant())) {
            // remove(key, value) 로 만료 판정 사이에 들어온 더 새 세션은 지우지 않는다(동시성 안전).
            sessions.remove(token, session)
            return null
        }
        return session
    }

    override fun deleteByToken(token: String) {
        sessions.remove(token)
        viewers.remove(token)
    }

    override fun markViewed(token: String) {
        viewers[token] = clock.instant()
    }

    override fun isWatched(token: String): Boolean {
        val last: Instant = viewers[token] ?: return false
        if (clock.instant().isBefore(last.plusSeconds(VIEWER_WINDOW_SECONDS))) {
            return true
        }
        viewers.remove(token, last)
        return false
    }

    override fun countActiveSessions(): Int {
        val now: Instant = clock.instant()
        return sessions.values.count { !it.isExpired(now) }
    }

    override fun findActiveSessions(): List<ShareSession> {
        val now: Instant = clock.instant()
        return sessions.values.filter { !it.isExpired(now) }
    }
}
