package io.soo.bueongi.core.domain.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareServiceAdminLocationTest : ShareServiceTestSupport() {

    @Test
    fun `getLocationForAdmin 은 활성 세션의 위치를 공개 조회와 동일한 뷰로 반환한다`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report(37.5, 127.04, remainingM = 500.0, remainingS = 360.0))
        val view = service.getLocationForAdmin(s.token)
        assertEquals(37.5, view.lat)
        assertEquals(127.04, view.lng)
        assertEquals(clock.now, view.updatedAt)
        assertFalse(view.expired)
        assertEquals(500.0, view.remainingM)
        assertEquals(360.0, view.remainingS)
    }

    @Test
    fun `getLocationForAdmin 은 시청자로 집계되지 않는다(isWatched 불변 - 비집계 핵심)`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report())
        repeat(3) { service.getLocationForAdmin(s.token) }
        assertFalse(store.isWatched(s.token))
    }

    @Test
    fun `대조 - 공개 getLocation 은 여전히 markViewed 를 호출한다(isWatched true, 회귀 없음)`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report())
        assertFalse(store.isWatched(s.token))
        service.getLocation(s.token)
        assertTrue(store.isWatched(s.token))
    }

    @Test
    fun `만료된 공유의 getLocationForAdmin 은 expired=true 에 좌표 null`() {
        val s = service.create(1)
        service.updateLocation(s.token, s.ownerSecret, report())
        clock.advanceSeconds(3600)
        val view = service.getLocationForAdmin(s.token)
        assertTrue(view.expired)
        assertNull(view.lat)
        assertNull(view.lng)
    }

    @Test
    fun `없는 token 의 getLocationForAdmin 은 expired=true(공개 조회와 동일 규약)`() {
        val view = service.getLocationForAdmin("nonexistent")
        assertTrue(view.expired)
        assertNull(view.lat)
        assertFalse(store.isWatched("nonexistent"))
    }

    @Test
    fun `생성됐으나 첫 위치 전이면 getLocationForAdmin 은 expired=false 에 좌표 null`() {
        val token = service.create(null).token
        val view = service.getLocationForAdmin(token)
        assertFalse(view.expired)
        assertNull(view.lat)
        assertNull(view.lng)
        assertNull(view.updatedAt)
        assertFalse(store.isWatched(token))
    }
}
