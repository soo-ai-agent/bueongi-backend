package io.soo.bueongi.share

import io.soo.bueongi.common.ShareSessionNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [ShareService] 단위 테스트. 공유 생명주기(생성/위치 갱신/조회) + 1~3시간 TTL + 프라이버시 불변식
 * (추측 불가 토큰·자동 만료·expired 플래그)을 결정론적 Clock 으로 고정한다. 저장소는 실제 [InMemoryLocationStore].
 */
class ShareServiceTest {

    private val clock = MutableTestClock()
    private val store = InMemoryLocationStore(clock)
    private val service = ShareService(store, clock, BASE_URL)

    private fun loc(lat: Double = 37.5, lng: Double = 127.0) = UpdateLocationRequest(lat = lat, lng = lng)

    @Test
    fun `create 는 시간 단위 TTL 뒤 만료, share_url 에 token 포함`() {
        val res = service.create(CreateShareRequest(expiresInHours = 2))
        assertEquals(clock.now.plusSeconds(2 * 3600L), res.expiresAt)
        assertEquals("$BASE_URL/share/${res.token}", res.shareUrl)
    }

    @Test
    fun `create 는 expires_in_hours 미지정 시 기본 1시간`() {
        val res = service.create(CreateShareRequest(expiresInHours = null))
        assertEquals(clock.now.plusSeconds(DEFAULT_EXPIRES_HOURS * 3600L), res.expiresAt)
    }

    @Test
    fun `create 는 null 요청도 기본 TTL 로 처리`() {
        val res = service.create(null)
        assertEquals(clock.now.plusSeconds(DEFAULT_EXPIRES_HOURS * 3600L), res.expiresAt)
    }

    @Test
    fun `token 은 매번 다르고 추측 불가능(128-bit URL-safe 22자)`() {
        val tokens = (1..50).map { service.create(null).token }
        assertEquals(50, tokens.toSet().size) // 충돌 없음
        tokens.forEach { t ->
            assertEquals(22, t.length) // 16바이트 base64url(패딩 제거) = 22자
            assertTrue(t.all { it.isLetterOrDigit() || it == '-' || it == '_' }) // URL-safe 문자만
        }
    }

    @Test
    fun `위치 갱신 후 조회로 같은 좌표 회수(왕복)`() {
        val token = service.create(null).token
        service.updateLocation(token, loc(37.5, 127.04))
        val view = service.getLocation(token)
        assertEquals(37.5, view.lat)
        assertEquals(127.04, view.lng)
        assertFalse(view.expired)
        assertEquals(clock.now, view.updatedAt)
    }

    @Test
    fun `생성됐으나 첫 위치 전이면 조회는 expired=false 에 좌표 null`() {
        val token = service.create(null).token
        val view = service.getLocation(token)
        assertFalse(view.expired)
        assertNull(view.lat)
        assertNull(view.lng)
        assertNull(view.updatedAt)
    }

    @Test
    fun `위치 갱신 응답은 갱신 시각과 공유 만료를 돌려준다`() {
        val created = service.create(CreateShareRequest(expiresInHours = 1))
        val token = created.token
        val res = service.updateLocation(token, loc())
        assertEquals(clock.now, res.updatedAt)
        assertEquals(created.expiresAt, res.expiresAt)
    }

    @Test
    fun `만료 후 조회는 expired=true 에 좌표 null`() {
        val token = service.create(CreateShareRequest(expiresInHours = 1)).token
        service.updateLocation(token, loc())
        clock.advanceSeconds(3600) // 정확히 1시간 → 만료
        val view = service.getLocation(token)
        assertTrue(view.expired)
        assertNull(view.lat)
        assertNull(view.lng)
    }

    @Test
    fun `없는 token 조회는 expired=true`() {
        val view = service.getLocation("nonexistent")
        assertTrue(view.expired)
    }

    @Test
    fun `없는 token 에 위치 갱신은 404`() {
        assertThrows(ShareSessionNotFoundException::class.java) {
            service.updateLocation("nonexistent", loc())
        }
    }

    @Test
    fun `만료된 공유에 위치 갱신은 404`() {
        val token = service.create(CreateShareRequest(expiresInHours = 1)).token
        clock.advanceSeconds(3600)
        assertThrows(ShareSessionNotFoundException::class.java) { service.updateLocation(token, loc()) }
    }

    @Test
    fun `서로 다른 공유는 격리된다`() {
        val a = service.create(null).token
        val b = service.create(null).token
        assertNotEquals(a, b)
        service.updateLocation(a, loc(37.1, 127.1))
        service.updateLocation(b, loc(38.2, 128.2))
        assertEquals(37.1, service.getLocation(a).lat)
        assertEquals(38.2, service.getLocation(b).lat)
    }

    private companion object {
        const val BASE_URL = "http://localhost:8119"
    }
}
