package io.soo.bueongi.storage.db.core.share

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
    private val valueOps = mock<ValueOperations<String, String>>()
    private val redis = mock<StringRedisTemplate> {
        on { opsForHash<String, String>() } doReturn hashOps
        on { opsForValue() } doReturn valueOps
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
    fun `delete 는 share·location·route·viewer 키를 함께 삭제한다(즉시 종료)`() {
        store.delete("tok")
        verify(redis).delete(listOf("share:tok", "location:tok", "route:tok", "viewer:tok"))
    }

    @Test
    fun `markViewed 는 viewer 키를 짧은 TTL 로 set, isWatched 는 hasKey 로 판정`() {
        store.markViewed("tok")
        verify(valueOps).set(eq("viewer:tok"), eq("1"), eq(Duration.ofSeconds(12)))

        whenever(redis.hasKey("viewer:tok")).thenReturn(true)
        assertTrue(store.isWatched("tok"))
    }

    @Test
    fun `remaining 이 있으면 location HASH 에 remaining_m·remaining_s 필드를 함께 쓴다`() {
        val now = clock.now
        store.save(
            ShareSession(
                token = "tok",
                ownerSecret = "sek",
                createdAt = now,
                expiresAt = now.plusSeconds(3600),
                location = StoredLocation(37.5, 127.04, now, remainingM = 500.0, remainingS = 360.0),
            ),
        )
        verify(hashOps).putAll(
            eq("location:tok"),
            eq(
                mapOf(
                    "lat" to "37.5",
                    "lng" to "127.04",
                    "updated_at" to now.toString(),
                    "remaining_m" to "500.0",
                    "remaining_s" to "360.0",
                ),
            ),
        )
    }

    @Test
    fun `save 는 route 를 JSON 문자열 값 키로 상한 TTL 과 함께 쓴다`() {
        val now = clock.now
        store.save(
            ShareSession(
                token = "tok",
                ownerSecret = "sek",
                createdAt = now,
                expiresAt = now.plusSeconds(3600),
                route = SAMPLE_ROUTE,
            ),
        )
        // route:{token} 값 키에 JSON + 상한 TTL(3600s). 위치가 없으므로 location HASH 는 쓰지 않는다.
        verify(valueOps).set(eq("route:tok"), any(), eq(Duration.ofSeconds(3600)))
    }

    @Test
    fun `find 는 route JSON 을 StoredRoute 로 역직렬화한다(왕복)`() {
        // save 가 쓰는 정확한 JSON 을 캡처해 find 가 되읽도록 연결(실제 직렬화 형식으로 왕복).
        val json = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(SAMPLE_ROUTE)
        whenever(hashOps.entries("share:tok")).thenReturn(
            mapOf(
                "created_at" to clock.now.toString(),
                "expires_at" to clock.now.plusSeconds(3600).toString(),
                "owner_secret" to "sek",
            ),
        )
        whenever(hashOps.entries("location:tok")).thenReturn(emptyMap())
        whenever(valueOps.get("route:tok")).thenReturn(json)

        val session = store.find("tok")!!
        assertEquals(SAMPLE_ROUTE, session.route)
    }

    @Test
    fun `find 는 route 키가 없으면 route=null`() {
        whenever(hashOps.entries("share:tok")).thenReturn(
            mapOf(
                "created_at" to clock.now.toString(),
                "expires_at" to clock.now.plusSeconds(3600).toString(),
                "owner_secret" to "sek",
            ),
        )
        whenever(hashOps.entries("location:tok")).thenReturn(emptyMap())
        whenever(valueOps.get("route:tok")).thenReturn(null)

        assertNull(store.find("tok")!!.route)
    }

    private companion object {
        val SAMPLE_ROUTE = StoredRoute(
            destination = StoredRouteDestination(name = "집", lat = 37.56, lng = 126.97),
            path = listOf(StoredCoord(37.5, 127.0), StoredCoord(37.55, 127.02)),
            markers = listOf(
                StoredMarker(
                    type = "cctv", lat = 37.5, lng = 127.0, x = 50.0, y = 50.0,
                    name = "CCTV", purpose = "생활방범", cameraCount = 2, address = "서울시", phone = null,
                ),
            ),
            totalDistanceM = 1234,
            totalTimeS = 900,
        )
    }
}
