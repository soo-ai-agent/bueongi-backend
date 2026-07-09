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

    private val clock = MutableTestClock()
    private val store = InMemoryLocationStore(clock)

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
        assertEquals(s, store.find("abc"))
    }

    @Test
    fun `없는 token 은 null`() {
        assertNull(store.find("nope"))
    }

    @Test
    fun `상한 만료가 지나면 find 는 null 이고 엔트리도 제거된다`() {
        store.save(session("abc", clock.now.plusSeconds(3600)))
        assertNotNull(store.find("abc")) // 만료 전엔 조회됨

        clock.advanceSeconds(3600) // 정확히 만료 시각 → 만료로 간주(now >= expiresAt)
        assertNull(store.find("abc"))

        // lazy 제거 확인: 시계를 되돌려도(현실엔 없지만 제거 여부 확인용) 다시 살아나지 않는다.
        clock.now = clock.now.minusSeconds(7200)
        assertNull(store.find("abc"))
    }

    @Test
    fun `save 는 같은 token 을 덮어쓴다(위치 갱신)`() {
        val base = session("abc", clock.now.plusSeconds(3600))
        store.save(base)
        val withLoc = base.copy(location = StoredLocation(37.5, 127.0, clock.now))
        store.save(withLoc)
        assertEquals(withLoc, store.find("abc"))
    }

    @Test
    fun `delete 는 공유를 즉시 제거한다(도착·중단)`() {
        store.save(session("abc", clock.now.plusSeconds(3600)))
        assertNotNull(store.find("abc"))
        store.delete("abc")
        assertNull(store.find("abc"))
    }

    @Test
    fun `없는 token delete 는 무해(멱등)`() {
        store.delete("nope") // 예외 없이 통과
        assertNull(store.find("nope"))
    }

    @Test
    fun `markViewed 후 isWatched 는 true, 유효시간(12초) 지나면 false`() {
        store.markViewed("abc")
        assertTrue(store.isWatched("abc"))
        clock.advanceSeconds(12) // 유효시간 경계 → 시청 종료
        assertFalse(store.isWatched("abc"))
    }

    @Test
    fun `조회 기록이 없으면 isWatched 는 false`() {
        assertFalse(store.isWatched("nope"))
    }

    @Test
    fun `delete 는 시청 기록도 함께 지운다`() {
        store.markViewed("abc")
        store.delete("abc")
        assertFalse(store.isWatched("abc"))
    }
}
