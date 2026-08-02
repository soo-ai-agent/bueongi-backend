package io.soo.bueongi.domain.share

import io.soo.bueongi.support.error.ShareSessionNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 공유 종료(end)의 계약을 고정한다: 올바른 secret 은 종료→이후 expired, 잘못된 secret 은 404 이고 공유 유지(임의 종료 차단),
 * 없는 token 은 멱등.
 */
class ShareServiceEndTest : ShareServiceTestSupport() {

    @Test
    fun `올바른 secret 으로 end 하면 이후 조회는 expired`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report())
        service.end(s.token, s.ownerSecret)
        assertTrue(service.getLocation(s.token).expired)
    }

    @Test
    fun `잘못된 secret 으로 end 는 404 이고 공유는 유지된다(임의 종료 차단)`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report(37.1, 127.1))
        assertThrows(ShareSessionNotFoundException::class.java) {
            service.end(s.token, "wrong")
        }
        assertEquals(37.1, service.getLocation(s.token).lat)
    }

    @Test
    fun `없는 token end 는 멱등(no-op)`() {
        service.end("nonexistent", "x")
    }
}
