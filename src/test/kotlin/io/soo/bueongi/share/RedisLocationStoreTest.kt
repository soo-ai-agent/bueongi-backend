package io.soo.bueongi.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.time.Instant

/**
 * [RedisLocationStore] 단위 테스트. 실제 Redis 없이(Mockito) **키 모델 계약**을 잠근다:
 * `share:{token}`/`location:{token}` HASH 필드명, 상한 TTL 적용, 조회 시 ShareSession 재구성.
 * (실 Redis 왕복은 인프라 통합 테스트 영역 — 여기선 키/필드/TTL 매핑의 회귀를 막는다.)
 */
class RedisLocationStoreTest {

    private val clock = MutableTestClock()
    private val hashOps = mock<HashOperations<String, String, String>>()
    private val redis = mock<StringRedisTemplate> {
        on { opsForHash<String, String>() } doReturn hashOps
    }
    private val store = RedisLocationStore(redis, clock)

    @Test
    fun `backend 라벨은 redis`() {
        assertEquals("redis", store.backend)
    }

    @Test
    fun `save 는 share·location 키에 HASH+상한 TTL 을 쓴다`() {
        val now = clock.now
        val expiresAt = now.plusSeconds(3600)
        store.save(
            ShareSession(
                token = "tok",
                ownerSecret = "sek",
                createdAt = now,
                expiresAt = expiresAt,
                location = StoredLocation(37.5, 127.04, now),
            ),
        )

        verify(hashOps).putAll(
            eq("share:tok"),
            eq(mapOf("created_at" to now.toString(), "expires_at" to expiresAt.toString(), "owner_secret" to "sek")),
        )
        verify(hashOps).putAll(
            eq("location:tok"),
            eq(mapOf("lat" to "37.5", "lng" to "127.04", "updated_at" to now.toString())),
        )
        verify(redis).expire(eq("share:tok"), eq(Duration.ofSeconds(3600)))
        verify(redis).expire(eq("location:tok"), eq(Duration.ofSeconds(3600)))
    }

    @Test
    fun `위치 없는 save 는 location 키를 만들지 않는다`() {
        val now = clock.now
        store.save(ShareSession("tok", "sek", now, now.plusSeconds(3600), location = null))
        verify(hashOps).putAll(eq("share:tok"), any())
        verify(hashOps, never()).putAll(eq("location:tok"), any())
    }

    @Test
    fun `find 는 빈 share HASH 면 null(키 만료·미존재)`() {
        whenever(hashOps.entries("share:tok")).thenReturn(emptyMap())
        assertNull(store.find("tok"))
    }

    @Test
    fun `find 는 share+location HASH 를 ShareSession 으로 재구성한다`() {
        val createdAt = Instant.parse("2026-06-19T12:00:00Z")
        val expiresAt = Instant.parse("2026-06-19T13:00:00Z")
        val updatedAt = Instant.parse("2026-06-19T12:30:00Z")
        whenever(hashOps.entries("share:tok")).thenReturn(
            mapOf("created_at" to createdAt.toString(), "expires_at" to expiresAt.toString(), "owner_secret" to "sek"),
        )
        whenever(hashOps.entries("location:tok")).thenReturn(
            mapOf("lat" to "37.5", "lng" to "127.04", "updated_at" to updatedAt.toString()),
        )

        val session = store.find("tok")!!
        assertEquals("tok", session.token)
        assertEquals("sek", session.ownerSecret)
        assertEquals(createdAt, session.createdAt)
        assertEquals(expiresAt, session.expiresAt)
        assertEquals(StoredLocation(37.5, 127.04, updatedAt), session.location)
    }

    @Test
    fun `find 는 share 만 있고 location 비면 location=null`() {
        whenever(hashOps.entries("share:tok")).thenReturn(
            mapOf(
                "created_at" to clock.now.toString(),
                "expires_at" to clock.now.plusSeconds(3600).toString(),
                "owner_secret" to "sek",
            ),
        )
        whenever(hashOps.entries("location:tok")).thenReturn(emptyMap())

        val session = store.find("tok")!!
        assertNull(session.location)
    }

    @Test
    fun `delete 는 share·location·viewer 키를 함께 삭제한다(즉시 종료)`() {
        store.delete("tok")
        verify(redis).delete(listOf("share:tok", "location:tok", "viewer:tok"))
    }

    @Test
    fun `markViewed 는 viewer 키를 짧은 TTL 로 set, isWatched 는 hasKey 로 판정`() {
        val valueOps = mock<ValueOperations<String, String>>()
        whenever(redis.opsForValue()).thenReturn(valueOps)

        store.markViewed("tok")
        verify(valueOps).set(eq("viewer:tok"), eq("1"), eq(Duration.ofSeconds(12)))

        whenever(redis.hasKey("viewer:tok")).thenReturn(true)
        assertTrue(store.isWatched("tok"))
    }
}
