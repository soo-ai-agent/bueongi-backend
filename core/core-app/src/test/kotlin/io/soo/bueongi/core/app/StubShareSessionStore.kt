package io.soo.bueongi.core.app

import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.store.ShareSessionStore

internal class StubShareSessionStore(
    override val backend: String,
) : ShareSessionStore {
    override fun save(session: ShareSession) = throw AssertionError("이 테스트는 save 를 호출하지 않는다")

    override fun findByToken(token: String): ShareSession? = throw AssertionError("이 테스트는 findByToken 을 호출하지 않는다")

    override fun deleteByToken(token: String) = throw AssertionError("이 테스트는 delete 를 호출하지 않는다")

    override fun markWatched(token: String) = throw AssertionError("이 테스트는 markWatched 를 호출하지 않는다")

    override fun isWatched(token: String): Boolean = throw AssertionError("이 테스트는 isWatched 를 호출하지 않는다")

    override fun countActive(): Int = throw AssertionError("이 테스트는 countActive 를 호출하지 않는다")

    override fun findActive(): List<ShareSession> = throw AssertionError("이 테스트는 findActive 를 호출하지 않는다")
}
