package io.soo.bueongi.core.app

import io.soo.bueongi.core.share.domain.service.ShareService
import io.soo.bueongi.storage.db.core.share.model.ShareSession
import io.soo.bueongi.storage.db.core.share.store.LocationStore
import java.time.Clock

internal fun stubShareService(backend: String = "memory"): ShareService =
    ShareService(StubLocationStore(backend), Clock.systemUTC(), "http://localhost:3619")

internal class StubLocationStore(
    override val backend: String,
) : LocationStore {
    override fun save(session: ShareSession) = throw AssertionError("이 테스트는 save 를 호출하지 않는다")

    override fun findByToken(token: String): ShareSession? = throw AssertionError("이 테스트는 findByToken 을 호출하지 않는다")

    override fun deleteByToken(token: String) = throw AssertionError("이 테스트는 delete 를 호출하지 않는다")

    override fun markViewed(token: String) = throw AssertionError("이 테스트는 markViewed 를 호출하지 않는다")

    override fun isWatched(token: String): Boolean = throw AssertionError("이 테스트는 isWatched 를 호출하지 않는다")

    override fun countActiveSessions(): Int = throw AssertionError("이 테스트는 countActiveSessions 를 호출하지 않는다")

    override fun findActiveSessions(): List<ShareSession> = throw AssertionError("이 테스트는 findActiveSessions 를 호출하지 않는다")
}
