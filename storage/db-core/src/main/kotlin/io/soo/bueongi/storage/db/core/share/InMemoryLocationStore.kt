package io.soo.bueongi.storage.db.core.share

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Redis 미주입(로컬/CI/MVP 기본) 시 동작하는 인메모리 공유 저장소. 장소 검색의 SeedPlaceProvider 와 동일 철학 —
 * 외부 인프라 없이 전 기능이 동작하고 동일 [LocationStore] 계약으로 테스트된다. 단일 인스턴스 가정(재시작 시 휘발).
 *
 * 공유 상한 만료는 [find] 가 주입된 [clock] 기준으로 lazy 하게 적용·제거한다(Redis EXPIRE 와 동치 거동) —
 * 별도 스위퍼 스레드 없이 접근 시점에 만료된 토큰을 정리한다.
 */
class InMemoryLocationStore(
    private val clock: Clock,
) : LocationStore {

    private val sessions = ConcurrentHashMap<String, ShareSession>()
    /** 보호자 조회 시각(토큰별). 유효시간이 지나면 lazy 제거 — '시청 중' 판정용. */
    private val viewers = ConcurrentHashMap<String, Instant>()

    override val backend: String = "memory"

    override fun save(session: ShareSession) {
        sessions[session.token] = session
    }

    override fun find(token: String): ShareSession? {
        val session = sessions[token] ?: return null
        if (session.isExpired(clock.instant())) {
            // 상한 만료 — lazy 제거 후 "없음"으로 응답(Redis TTL 과 동일 거동).
            // remove(key, value) 로 만료 판정 사이에 들어온 더 새 세션은 지우지 않는다(동시성 안전).
            sessions.remove(token, session)
            return null
        }
        return session
    }

    override fun delete(token: String) {
        // 즉시 종료(도착/중단). 없는 토큰도 무해(멱등) — Redis delete 와 동일 거동.
        sessions.remove(token)
        viewers.remove(token)
    }

    override fun markViewed(token: String) {
        viewers[token] = clock.instant()
    }

    override fun isWatched(token: String): Boolean {
        val last = viewers[token] ?: return false
        if (clock.instant().isBefore(last.plusSeconds(VIEWER_WINDOW_SECONDS))) return true
        viewers.remove(token, last) // 유효시간 경과 → lazy 제거(Redis TTL 동치).
        return false
    }

    private companion object {
        /** 보호자 조회 후 이 시간(초) 동안 '시청 중'으로 본다(Redis 와 동일). */
        const val VIEWER_WINDOW_SECONDS = 12L
    }
}
