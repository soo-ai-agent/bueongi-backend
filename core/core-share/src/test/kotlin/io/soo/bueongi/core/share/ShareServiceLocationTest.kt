package io.soo.bueongi.core.share

import io.soo.bueongi.core.enums.ShareProgressStatus
import io.soo.bueongi.core.share.domain.error.ShareSessionNotFoundException
import io.soo.bueongi.storage.db.share.model.StoredCoord
import io.soo.bueongi.storage.db.share.model.StoredRoute
import io.soo.bueongi.storage.db.share.model.StoredRouteDestination
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

    @Test
    fun `정상 진행 중이면 status 는 in_progress 다`() {
        val s = service.create(1, etaSeconds = 600)
        service.updateLocation(s.token, s.ownerSecret, report(37.5, 127.04))
        val view = service.getLocation(s.token)
        assertEquals(ShareProgressStatus.IN_PROGRESS, view.status)
    }

    @Test
    fun `예정 초과 + 정지 + 목적지 근처면 status 는 arrival_likely 다`() {
        val s = service.create(1, etaSeconds = 10)
        service.saveRoute(s.token, s.ownerSecret, routeTo(lat = 37.5, lng = 127.04))
        service.updateLocation(s.token, s.ownerSecret, report(37.5, 127.04))
        // eta(10) + 여유(60) + 초과 임계(300) 를 넘기고, 위치 보고도 같은 시간만큼 멈춘 상태.
        clock.advanceSeconds(10 + 60 + 300 + 1)
        val view = service.getLocation(s.token)
        assertEquals(ShareProgressStatus.ARRIVAL_LIKELY, view.status)
    }

    @Test
    fun `예정 초과 + 정지 + 목적지에서 멀면 status 는 missed_arrival_suspect 다`() {
        val s = service.create(1, etaSeconds = 10)
        service.saveRoute(s.token, s.ownerSecret, routeTo(lat = 37.6, lng = 127.04))
        service.updateLocation(s.token, s.ownerSecret, report(37.5, 127.04))
        clock.advanceSeconds(10 + 60 + 300 + 1)
        val view = service.getLocation(s.token)
        assertEquals(ShareProgressStatus.MISSED_ARRIVAL_SUSPECT, view.status)
    }

    @Test
    fun `목적지 근처라도 경로상 남은 거리가 멀면 status 는 missed_arrival_suspect 다`() {
        val s = service.create(1, etaSeconds = 10)
        service.saveRoute(s.token, s.ownerSecret, routeTo(lat = 37.5, lng = 127.04))
        service.updateLocation(s.token, s.ownerSecret, report(37.5, 127.04, remainingMeters = 500.0))
        clock.advanceSeconds(10 + 60 + 300 + 1)
        val view = service.getLocation(s.token)
        assertEquals(ShareProgressStatus.MISSED_ARRIVAL_SUSPECT, view.status)
    }

    private fun routeTo(lat: Double, lng: Double): StoredRoute {
        return StoredRoute(
            destination = StoredRouteDestination(name = "집", lat = lat, lng = lng),
            path = listOf(StoredCoord(lat, lng)),
            markers = emptyList(),
            totalDistanceMeters = 0,
            totalTimeSeconds = 0,
            type = "safe",
        )
    }
}
