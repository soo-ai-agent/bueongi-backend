package io.soo.bueongi.storage.db.core.share

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Redis 미주입(로컬/CI/MVP 기본) 시 동작하는 인메모리 공유 저장소. 장소 검색의 SeedPlaceProvider 와 동일 철학 —
 * 외부 인프라 없이 전 기능이 동작하고 동일 [LocationStore] 계약으로 테스트된다. 단일 인스턴스 가정(재시작 시 휘발).
 *
 * 공유 상한 만료는 [findByToken] 이 주입된 [clock] 기준으로 lazy 하게 적용·제거한다(Redis EXPIRE 와 동치 거동) —
 * 별도 스위퍼 스레드 없이 접근 시점에 만료된 토큰을 정리한다.
 */
class InMemoryLocationStore(
    private val clock: Clock,
) : LocationStore {

    private val sessions: ConcurrentHashMap<String, ShareSession> = ConcurrentHashMap<String, ShareSession>()
    /** 보호자 조회 시각(토큰별). 유효시간이 지나면 lazy 제거 — '시청 중' 판정용. */
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
        // 카운트와 동일 기준 — 만료 세션은 제외해 열거한다(Redis SCAN+미존재 키 제외와 동치).
        val now: Instant = clock.instant()
        return sessions.values.filter { !it.isExpired(now) }
    }
}
