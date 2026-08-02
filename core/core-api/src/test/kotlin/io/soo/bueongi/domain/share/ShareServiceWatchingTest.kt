package io.soo.bueongi.domain.share

import io.soo.bueongi.support.error.ShareSessionNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 관리자 시청 상태 조회(getWatchingStatus)의 계약을 고정한다: 보호자 조회 유무에 따른 watching 전이, 12초 유효시간,
 * 관리자 본인 비집계, owner_secret 검증(404), 현재 위치·remaining 동봉, 만료 처리.
 */
class ShareServiceWatchingTest : ShareServiceTestSupport() {

    @Test
    fun `보호자 조회가 있으면 watching=true, 없으면 false`() {
        val s = service.create(null)
        assertFalse(service.getWatchingStatus(s.token, s.ownerSecret).watching)
        service.getLocation(s.token)
        assertTrue(service.getWatchingStatus(s.token, s.ownerSecret).watching)
    }

    @Test
    fun `시청 유효시간(12초)이 지나면 watching=false`() {
        val s = service.create(null)
        service.getLocation(s.token)
        clock.advanceSeconds(12)
        assertFalse(service.getWatchingStatus(s.token, s.ownerSecret).watching)
    }

    @Test
    fun `getWatchingStatus 호출 자체는 시청자로 집계되지 않는다(관리자 본인 제외)`() {
        val s = service.create(null)
        repeat(3) { service.getWatchingStatus(s.token, s.ownerSecret) }
        assertFalse(service.getWatchingStatus(s.token, s.ownerSecret).watching)
    }

    @Test
    fun `잘못된 owner_secret 으로 watching 조회는 404`() {
        val s = service.create(null)
        assertThrows(ShareSessionNotFoundException::class.java) {
            service.getWatchingStatus(s.token, "wrong")
        }
    }

    @Test
    fun `watching 조회는 현재 공유 위치도 함께 돌려준다(관리자 미리보기)`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report(37.55, 127.02))
        val res = service.getWatchingStatus(s.token, s.ownerSecret)
        assertEquals(37.55, res.lat)
        assertEquals(127.02, res.lng)
        assertEquals(clock.now, res.updatedAt)
        assertFalse(res.expired)
    }

    @Test
    fun `위치 전 watching 조회는 좌표 null 에 expired=false`() {
        val s = service.create(null)
        val res = service.getWatchingStatus(s.token, s.ownerSecret)
        assertNull(res.lat)
        assertNull(res.lng)
        assertFalse(res.expired)
    }

    @Test
    fun `만료된 공유의 watching 조회는 expired=true(관리자 미리보기도 종료)`() {
        val s = service.create(1)
        service.updateLocation(s.token, s.ownerSecret, report())
        clock.advanceSeconds(3600)
        val res = service.getWatchingStatus(s.token, s.ownerSecret)
        assertTrue(res.expired)
        assertNull(res.lat)
        assertFalse(res.watching)
    }

    @Test
    fun `watching 조회도 remaining 을 함께 돌려준다(관리자 미리보기 일관성)`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report(remainingM = 250.0, remainingS = 120.0))
        val res = service.getWatchingStatus(s.token, s.ownerSecret)
        assertEquals(250.0, res.remainingM)
        assertEquals(120.0, res.remainingS)
    }
}
