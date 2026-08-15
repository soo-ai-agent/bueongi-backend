package io.soo.bueongi.share

import io.soo.bueongi.storage.db.core.share.StoredCoord
import io.soo.bueongi.storage.db.core.share.StoredMarker
import io.soo.bueongi.storage.db.core.share.StoredRoute
import io.soo.bueongi.storage.db.core.share.StoredRouteDestination
import io.soo.bueongi.support.error.ShareSessionNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareServiceRouteTest : ShareServiceTestSupport() {

    private fun route() = StoredRoute(
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

    @Test
    fun `경로 저장 후 조회로 같은 컨텍스트 회수(왕복)`() {
        val s = service.create(null)
        service.saveRoute(s.token, s.ownerSecret, route())
        val view = service.getRoute(s.token)
        assertFalse(view.expired)
        val r = view.route!!
        assertEquals("집", r.destination.name)
        assertEquals(37.56, r.destination.lat)
        assertEquals(2, r.path.size)
        assertEquals(1, r.markers.size)
        assertEquals("cctv", r.markers[0].type)
        assertEquals(2, r.markers[0].cameraCount)
        assertEquals(1234, r.totalDistanceM)
        assertEquals(900, r.totalTimeS)
    }

    @Test
    fun `경로 미저장 시 조회는 expired=false 에 route null`() {
        val s = service.create(null)
        val view = service.getRoute(s.token)
        assertFalse(view.expired)
        assertNull(view.route)
    }

    @Test
    fun `만료된 공유의 경로 조회는 expired=true 에 route null`() {
        val s = service.create(1)
        service.saveRoute(s.token, s.ownerSecret, route())
        clock.advanceSeconds(3600)
        val view = service.getRoute(s.token)
        assertTrue(view.expired)
        assertNull(view.route)
    }

    @Test
    fun `없는 token 경로 조회는 expired=true`() {
        assertTrue(service.getRoute("nonexistent").expired)
    }

    @Test
    fun `없는 token 에 경로 저장은 404`() {
        assertThrows(ShareSessionNotFoundException::class.java) {
            service.saveRoute("nonexistent", "any-secret", route())
        }
    }

    @Test
    fun `잘못된 owner_secret 으로 경로 저장은 404(경로 조작 차단)`() {
        val s = service.create(null)
        assertThrows(ShareSessionNotFoundException::class.java) {
            service.saveRoute(s.token, "wrong-secret", route())
        }
    }

    @Test
    fun `경로와 위치는 독립 저장된다(경로 저장이 위치를 지우지 않고 그 반대도)`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report(37.1, 127.1))
        service.saveRoute(s.token, s.ownerSecret, route())
        assertEquals(37.1, service.getLocation(s.token).lat)
        service.updateLocation(s.token, s.ownerSecret, report(37.2, 127.2))
        assertEquals("집", service.getRoute(s.token).route!!.destination.name)
    }
}
