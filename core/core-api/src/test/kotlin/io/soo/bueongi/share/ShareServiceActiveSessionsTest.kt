package io.soo.bueongi.share

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareServiceActiveSessionsTest : ShareServiceTestSupport() {

    @Test
    fun `listActiveSessions 는 세션이 없으면 빈 목록`() {
        assertTrue(service.listActiveSessions().isEmpty())
    }

    @Test
    fun `listActiveSessions 는 활성 세션을 매핑한다(shareUrl·ttl·viewerActive·lat 파생)`() {
        val s = service.create(1)
        service.updateLocation(s.token, s.ownerSecret, report(37.5, 127.04))
        val list = service.listActiveSessions()
        assertEquals(1, list.size)
        val v = list[0]
        assertEquals(s.token, v.token)
        assertEquals("$BASE_URL/share/${s.token}", v.shareUrl)
        assertEquals(37.5, v.lat)
        assertEquals(127.04, v.lng)
        assertEquals(clock.now.toEpochMilli(), v.updatedAtMs)
        assertEquals(3600, v.ttlSeconds)
        assertFalse(v.viewerActive)
    }

    @Test
    fun `위치 미보고 세션은 lat·lng·updatedAtMs 가 null`() {
        service.create(null)
        val v = service.listActiveSessions().single()
        assertNull(v.lat)
        assertNull(v.lng)
        assertNull(v.updatedAtMs)
    }

    @Test
    fun `보호자 조회가 있으면 listActiveSessions 의 viewerActive=true`() {
        val s = service.create(null)
        service.getLocation(s.token)
        assertTrue(service.listActiveSessions().single().viewerActive)
    }

    @Test
    fun `listActiveSessions 는 활성만 남기고 만료를 제외한다`() {
        service.create(1)
        clock.advanceSeconds(1800)
        val live = service.create(1)
        clock.advanceSeconds(1800)
        val list = service.listActiveSessions()
        assertEquals(listOf(live.token), list.map { it.token })
    }

    @Test
    fun `listActiveSessions 정렬 - updatedAtMs 내림차순, 위치 미보고는 마지막`() {
        val recent = service.create(null)
        val older = service.create(null)
        val noLocation = service.create(null)
        clock.advanceSeconds(10)
        service.updateLocation(older.token, older.ownerSecret, report())
        clock.advanceSeconds(10)
        service.updateLocation(recent.token, recent.ownerSecret, report())
        val tokens = service.listActiveSessions().map { it.token }
        assertEquals(listOf(recent.token, older.token, noLocation.token), tokens)
        assertNull(service.listActiveSessions().last().updatedAtMs)
    }

    @Test
    fun `listActiveSessions 동률(위치 미보고 그룹)은 token 오름차순`() {
        val x = service.create(null)
        val y = service.create(null)
        val expected = listOf(x.token, y.token).sorted()
        assertEquals(expected, service.listActiveSessions().map { it.token })
    }

    @Test
    fun `listActiveSessions 응답 어디에도 owner_secret 이 없다`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report())
        val json = jacksonObjectMapper().writeValueAsString(service.listActiveSessions())
        assertFalse(json.contains(s.ownerSecret))
        assertFalse(json.contains("ownerSecret"))
        assertFalse(json.contains("owner_secret"))
    }
}
