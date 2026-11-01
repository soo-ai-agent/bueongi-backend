package io.soo.bueongi.core.share

import io.soo.bueongi.core.share.domain.error.ShareSessionNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareServiceLocationTest : ShareServiceTestSupport() {
    @Test
    fun `위치 갱신 후 조회로 같은 좌표 회수(왕복)`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report(37.5, 127.04))
        val view = service.getLocation(s.token)
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
        val created = service.create(1)
        val res = service.updateLocation(created.token, created.ownerSecret, report())
        assertEquals(clock.now, res.updatedAt)
        assertEquals(created.expiresAt, res.expiresAt)
    }

    @Test
    fun `만료 후 조회는 expired=true 에 좌표 null`() {
        val created = service.create(1)
        service.updateLocation(created.token, created.ownerSecret, report())
        clock.advanceSeconds(3600)
        val view = service.getLocation(created.token)
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
            service.updateLocation("nonexistent", "any-secret", report())
        }
    }

    @Test
    fun `만료된 공유에 위치 갱신은 404`() {
        val created = service.create(1)
        clock.advanceSeconds(3600)
        assertThrows(ShareSessionNotFoundException::class.java) { service.updateLocation(created.token, created.ownerSecret, report()) }
    }

    @Test
    fun `서로 다른 공유는 격리된다`() {
        val a = service.create(null)
        val b = service.create(null)
        assertNotEquals(a.token, b.token)
        service.updateLocation(a.token, a.ownerSecret, report(37.1, 127.1))
        service.updateLocation(b.token, b.ownerSecret, report(38.2, 128.2))
        assertEquals(37.1, service.getLocation(a.token).lat)
        assertEquals(38.2, service.getLocation(b.token).lat)
    }

    @Test
    fun `잘못된 owner_secret 으로 위치 갱신은 404(위치 조작 차단)`() {
        val s = service.create(null)
        assertThrows(ShareSessionNotFoundException::class.java) {
            service.updateLocation(s.token, "wrong-secret", report())
        }
    }

    @Test
    fun `owner_secret 누락(null)이면 위치 갱신은 404`() {
        val s = service.create(null)
        assertThrows(ShareSessionNotFoundException::class.java) {
            service.updateLocation(s.token, null, report())
        }
    }

    @Test
    fun `remaining_meters·remaining_seconds 는 위치 조회로 왕복된다`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report(remainingMeters = 500.0, remainingSeconds = 360.0))
        val view = service.getLocation(s.token)
        assertEquals(500.0, view.remainingMeters)
        assertEquals(360.0, view.remainingSeconds)
    }

    @Test
    fun `remaining 미보고 시 위치 조회의 remaining 은 null`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report())
        val view = service.getLocation(s.token)
        assertNull(view.remainingMeters)
        assertNull(view.remainingSeconds)
    }
}
