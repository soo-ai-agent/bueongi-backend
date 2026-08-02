package io.soo.bueongi.storage.db.core.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [InMemoryLocationStore] 단위 테스트. 저장소 계약([LocationStore])의 핵심 불변식을 결정론적 Clock 으로 고정한다:
 * 저장/조회 왕복, 상한 만료의 lazy 제거(Redis EXPIRE 동치), 위치 갱신 덮어쓰기, backend 라벨.
 */
class InMemoryLocationStoreTest {

    private val clock: MutableTestClock = MutableTestClock()
    private val store: InMemoryLocationStore = InMemoryLocationStore(clock)

    private fun session(token: String, expiresAt: Instant) =
        ShareSession(token = token, ownerSecret = "secret-$token", createdAt = clock.now, expiresAt = expiresAt)

    @Test
    fun `backend 라벨은 memory`() {
        assertEquals("memory", store.backend)
    }

    @Test
    fun `저장한 공유를 같은 token 으로 조회한다`() {
        val s = session("abc", clock.now.plusSeconds(3600))
        store.save(s)
        assertEquals(s, store.findByToken("abc"))
    }

    @Test
    fun `없는 token 은 null`() {
        assertNull(store.findByToken("nope"))
    }

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
    fun `save 는 같은 token 을 덮어쓴다(위치 갱신)`() {
        val base = session("abc", clock.now.plusSeconds(3600))
        store.save(base)
        val withLoc = base.copy(location = StoredLocation(37.5, 127.0, clock.now))
        store.save(withLoc)
        assertEquals(withLoc, store.findByToken("abc"))
    }

    @Test
    fun `delete 는 공유를 즉시 제거한다(도착·중단)`() {
        store.save(session("abc", clock.now.plusSeconds(3600)))
        assertNotNull(store.findByToken("abc"))
        store.deleteByToken("abc")
        assertNull(store.findByToken("abc"))
    }

    @Test
    fun `없는 token delete 는 무해(멱등)`() {
        store.deleteByToken("nope")
        assertNull(store.findByToken("nope"))
    }

    @Test
    fun `markViewed 후 isWatched 는 true, 유효시간(12초) 지나면 false`() {
        store.markViewed("abc")
        assertTrue(store.isWatched("abc"))
        clock.advanceSeconds(12)
        assertFalse(store.isWatched("abc"))
    }

    @Test
    fun `조회 기록이 없으면 isWatched 는 false`() {
        assertFalse(store.isWatched("nope"))
    }

    @Test
    fun `delete 는 시청 기록도 함께 지운다`() {
        store.markViewed("abc")
        store.deleteByToken("abc")
        assertFalse(store.isWatched("abc"))
    }

    @Test
    fun `countActiveSessions 는 저장된 미만료 세션 수를 센다`() {
        assertEquals(0, store.countActiveSessions())
        store.save(session("a", clock.now.plusSeconds(3600)))
        store.save(session("b", clock.now.plusSeconds(3600)))
        assertEquals(2, store.countActiveSessions())
    }

    @Test
    fun `countActiveSessions 는 만료 세션을 제외한다(Redis TTL 동치)`() {
        store.save(session("live", clock.now.plusSeconds(3600)))
        store.save(session("dead", clock.now.plusSeconds(10)))
        clock.advanceSeconds(20)
        assertEquals(1, store.countActiveSessions())
    }

    @Test
    fun `findActiveSessions 는 저장된 미만료 세션을 반환한다`() {
        assertTrue(store.findActiveSessions().isEmpty())
        val a = session("a", clock.now.plusSeconds(3600))
        val b = session("b", clock.now.plusSeconds(3600))
        store.save(a)
        store.save(b)
        assertEquals(setOf(a, b), store.findActiveSessions().toSet())
    }

    @Test
    fun `findActiveSessions 는 만료 세션을 제외한다(Redis SCAN 동치)`() {
        store.save(session("live", clock.now.plusSeconds(3600)))
        store.save(session("dead", clock.now.plusSeconds(10)))
        clock.advanceSeconds(20)
        assertEquals(listOf("live"), store.findActiveSessions().map { it.token })
    }
}
