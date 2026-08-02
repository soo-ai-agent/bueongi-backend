package io.soo.bueongi.storage.db.core.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.Cursor
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.ScanOptions
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

    private val clock: MutableTestClock = MutableTestClock()
    private val hashOps = mock<HashOperations<String, String, String>>()
    private val valueOps = mock<ValueOperations<String, String>>()
    private val redis = mock<StringRedisTemplate> {
        on { opsForHash<String, String>() } doReturn hashOps
        on { opsForValue() } doReturn valueOps
    }
    private val store: RedisLocationStore = RedisLocationStore(redis, clock)

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
        assertNull(store.findByToken("tok"))
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

        val session = store.findByToken("tok")!!
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

        val session = store.findByToken("tok")!!
        assertNull(session.location)
    }

    @Test
    fun `delete 는 share·location·route·viewer 키를 함께 삭제한다(즉시 종료)`() {
        store.deleteByToken("tok")
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
        verify(valueOps).set(eq("route:tok"), any(), eq(Duration.ofSeconds(3600)))
    }

    @Test
    fun `find 는 route JSON 을 StoredRoute 로 역직렬화한다(왕복)`() {
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

        val session = store.findByToken("tok")!!
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

        assertNull(store.findByToken("tok")!!.route)
    }

    @Test
    fun `countActiveSessions 는 share 키를 SCAN 으로 세고 KEYS 를 쓰지 않는다`() {
        val cursor = mock<Cursor<String>>()
        whenever(cursor.hasNext()).thenReturn(true, true, false)
        whenever(cursor.next()).thenReturn("share:a", "share:b")
        whenever(redis.scan(any<ScanOptions>())).thenReturn(cursor)

        assertEquals(2, store.countActiveSessions())
        verify(cursor).close()
    }

    @Test
    fun `findActiveSessions 는 SCAN 세션키에서 토큰을 뽑아 세션을 복원한다`() {
        val cursor = mock<Cursor<String>>()
        whenever(cursor.hasNext()).thenReturn(true, false)
        whenever(cursor.next()).thenReturn("share:tok")
        whenever(redis.scan(any<ScanOptions>())).thenReturn(cursor)
        whenever(hashOps.entries("share:tok")).thenReturn(
            mapOf(
                "created_at" to clock.now.toString(),
                "expires_at" to clock.now.plusSeconds(3600).toString(),
                "owner_secret" to "sek",
            ),
        )
        whenever(hashOps.entries("location:tok")).thenReturn(emptyMap())
        whenever(valueOps.get("route:tok")).thenReturn(null)

        val active = store.findActiveSessions()
        assertEquals(1, active.size)
        assertEquals("tok", active[0].token)
        verify(cursor).close()
    }

    @Test
    fun `save 는 expectedArrivalAt 을 share HASH 에 쓰고 find 가 되읽는다(왕복)`() {
        val now = clock.now
        val expected = now.plusSeconds(1800)
        store.save(ShareSession("tok", "sek", now, now.plusSeconds(3600), expectedArrivalAt = expected))
        verify(hashOps).putAll(
            eq("share:tok"),
            eq(
                mapOf(
                    "created_at" to now.toString(),
                    "expires_at" to now.plusSeconds(3600).toString(),
                    "owner_secret" to "sek",
                    "expected_arrival_at" to expected.toString(),
                ),
            ),
        )

        whenever(hashOps.entries("share:tok")).thenReturn(
            mapOf(
                "created_at" to now.toString(),
                "expires_at" to now.plusSeconds(3600).toString(),
                "owner_secret" to "sek",
                "expected_arrival_at" to expected.toString(),
            ),
        )
        whenever(hashOps.entries("location:tok")).thenReturn(emptyMap())
        whenever(valueOps.get("route:tok")).thenReturn(null)
        assertEquals(expected, store.findByToken("tok")!!.expectedArrivalAt)
    }

    @Test
    fun `읽기 중 Redis 연결 실패는 LocationStoreUnavailableException 으로 변환된다`() {
        whenever(hashOps.entries(any())).thenThrow(RedisConnectionFailureException("redis down"))
        assertThrows<LocationStoreUnavailableException> { store.findByToken("tok") }
    }

    @Test
    fun `쓰기 중 Redis 연결 실패는 LocationStoreUnavailableException 으로 변환된다`() {
        whenever(hashOps.putAll(any(), any())).thenThrow(RedisConnectionFailureException("redis down"))
        assertThrows<LocationStoreUnavailableException> {
            store.save(ShareSession("tok", "sek", clock.now, clock.now.plusSeconds(3600)))
        }
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
