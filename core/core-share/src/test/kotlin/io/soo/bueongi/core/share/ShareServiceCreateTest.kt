package io.soo.bueongi.core.share

import io.soo.bueongi.core.share.domain.model.DEFAULT_EXPIRES_HOURS
import io.soo.bueongi.core.share.domain.model.MISSED_ARRIVAL_GRACE_SECONDS
import io.soo.bueongi.storage.db.core.share.ShareSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareServiceCreateTest : ShareServiceTestSupport() {

    @Test
    fun `create 는 시간 단위 TTL 뒤 만료, share_url 에 token 포함`() {
        val res = service.create(2)
        assertEquals(clock.now.plusSeconds(2 * 3600L), res.expiresAt)
        assertEquals("$BASE_URL/share/${res.token}", res.shareUrl)
    }

    @Test
    fun `create 는 expires_in_hours 미지정 시 기본 1시간`() {
        val res = service.create(null)
        assertEquals(clock.now.plusSeconds(DEFAULT_EXPIRES_HOURS * 3600L), res.expiresAt)
    }

    @Test
    fun `create 는 eta_seconds 로 예정 도착 시각(+GRACE)을 잡는다`() {
        val res = service.create(1, etaSeconds = 600)
        assertEquals(clock.now.plusSeconds(600 + MISSED_ARRIVAL_GRACE_SECONDS), store.findByToken(res.token)!!.expectedArrivalAt)
    }

    @Test
    fun `create 는 eta 미제공 시 예정 도착 시각을 두지 않는다(감시 제외)`() {
        val res = service.create(1)
        assertNull(store.findByToken(res.token)!!.expectedArrivalAt)
    }

    @Test
    fun `getLocation 은 예정 도착 시각을 여유 넘겨 초과하면 overdue=true`() {
        val res = service.create(3, etaSeconds = 60)
        service.updateLocation(res.token, res.ownerSecret, report())
        clock.advanceSeconds(60 + MISSED_ARRIVAL_GRACE_SECONDS + ShareSession.OVERDUE_SECONDS + 1)
        assertTrue(service.getLocation(res.token).overdue)
    }

    @Test
    fun `getLocation 은 예정 도착 전이면 overdue=false`() {
        val res = service.create(3, etaSeconds = 600)
        service.updateLocation(res.token, res.ownerSecret, report())
        assertFalse(service.getLocation(res.token).overdue)
    }

    @Test
    fun `create 는 null 요청도 기본 TTL 로 처리`() {
        val res = service.create(null)
        assertEquals(clock.now.plusSeconds(DEFAULT_EXPIRES_HOURS * 3600L), res.expiresAt)
    }

    @Test
    fun `token 은 매번 다르고 추측 불가능(128-bit URL-safe 22자)`() {
        val tokens = (1..50).map { service.create(null).token }
        assertEquals(50, tokens.toSet().size)
        tokens.forEach { t ->
            assertEquals(22, t.length)
            assertTrue(t.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        }
    }
}
