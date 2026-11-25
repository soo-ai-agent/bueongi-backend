package io.soo.bueongi.storage.db.share.store

import io.soo.bueongi.storage.db.share.model.ShareSession

interface ShareSessionStore {
    // health 응답에 그대로 나간다: 운영은 "redis"(테스트 대역은 "memory") — 자격증명·주소는 노출 금지.
    val backend: String

    fun save(session: ShareSession)

    // 계약: 만료 강제는 저장소 책임이다(Redis native TTL).
    fun findByToken(token: String): ShareSession?

    fun deleteByToken(token: String)

    fun markViewed(token: String)

    fun isWatched(token: String): Boolean

    fun countActive(): Int

    fun findActive(): List<ShareSession>

    companion object {
        // 보호자 폴링 주기(5초)의 2~3배 여유를 유지해야 한다.
        const val VIEWER_WINDOW_SECONDS: Long = 12L
    }
}
