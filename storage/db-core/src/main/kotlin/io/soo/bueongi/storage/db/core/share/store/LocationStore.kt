package io.soo.bueongi.storage.db.core.share.store

import io.soo.bueongi.storage.db.core.share.model.ShareSession

interface LocationStore {
    /** health 라벨: "redis" | "memory" — 자격증명·주소는 노출하지 않는다. */
    val backend: String

    fun save(session: ShareSession)

    /** 계약: 없거나 상한 만료면 null — 만료 강제는 저장소 책임(Redis native TTL / 인메모리 lazy 비교 동치). */
    fun findByToken(token: String): ShareSession?

    fun deleteByToken(token: String)

    fun markViewed(token: String)

    fun isWatched(token: String): Boolean

    fun countActiveSessions(): Int

    fun findActiveSessions(): List<ShareSession>
}

/** '시청 중' 판정 창(초) — 보호자 폴링 주기(5초)의 2~3배 여유를 유지해야 한다. */
const val VIEWER_WINDOW_SECONDS: Long = 12L
