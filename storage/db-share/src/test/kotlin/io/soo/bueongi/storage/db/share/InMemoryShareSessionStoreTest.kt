package io.soo.bueongi.storage.db.share

import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.store.InMemoryShareSessionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class InMemoryShareSessionStoreTest {
    private val clock = MutableTestClock()
    private val store = InMemoryShareSessionStore(clock)

    private fun session(token: String, expiresAt: Instant) =
        ShareSession(token = token, ownerSecret = "secret-$token", createdAt = clock.now, expiresAt = expiresAt)

    @Test
    fun `상한 만료가 지나면 find 는 null 이고 엔트리도 제거된다`() {
        store.save(session("abc", clock.now.plusSeconds(3600)))
        assertNotNull(store.findByToken("abc"))

        clock.advanceSeconds(3600)
        assertNull(store.findByToken("abc"))

        clock.now = clock.now.minusSeconds(7200)
        assertNull(store.findByToken("abc"))
    }

    @Test
    fun `markWatched 후 isWatched 는 true, 유효시간(12초) 지나면 false`() {
        store.markWatched("abc")
        assertTrue(store.isWatched("abc"))
        clock.advanceSeconds(12)
        assertFalse(store.isWatched("abc"))
    }

    @Test
    fun `findActiveSessions 는 만료 세션을 제외한다(Redis SCAN 동치)`() {
        store.save(session("live", clock.now.plusSeconds(3600)))
        store.save(session("dead", clock.now.plusSeconds(10)))
        clock.advanceSeconds(20)
        assertEquals(listOf("live"), store.findActive().map { it.token })
    }
}
