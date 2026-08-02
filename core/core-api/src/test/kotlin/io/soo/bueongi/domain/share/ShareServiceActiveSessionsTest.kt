package io.soo.bueongi.domain.share

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 활성 세션 목록(listActiveSessions)의 매핑(shareUrl·ttl·viewerActive·lat 파생), 만료 제외, 정렬 규칙
 * (updatedAtMs 내림차순·미보고 마지막·동률 token 오름차순), owner_secret 미노출을 고정한다.
 */
class ShareServiceActiveSessionsTest : ShareServiceTestSupport() {

    @Test
    fun `listActiveSessions 는 세션이 없으면 빈 목록`() {
        assertTrue(service.listActiveSessions().isEmpty())
    }

    @Test
    fun `listActiveSessions 는 활성 세션을 매핑한다(shareUrl·ttl·viewerActive·lat 파생)`() {
        val s = service.create(1) // 상한 만료 = now + 3600
        service.updateLocation(s.token, s.ownerSecret, report(37.5, 127.04))
        val list = service.listActiveSessions()
        assertEquals(1, list.size)
        val v = list[0]
        assertEquals(s.token, v.token)
        assertEquals("$BASE_URL/share/${s.token}", v.shareUrl)
        assertEquals(37.5, v.lat)
        assertEquals(127.04, v.lng)
        assertEquals(clock.now.toEpochMilli(), v.updatedAtMs)
        assertEquals(3600, v.ttlSeconds) // expiresAt 파생(추가 저장소 왕복 없음)
        assertFalse(v.viewerActive) // 아직 보호자 조회 없음
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
        service.getLocation(s.token) // 보호자(뷰어) 조회 → 시청 기록
        assertTrue(service.listActiveSessions().single().viewerActive)
    }

    @Test
    fun `listActiveSessions 는 활성만 남기고 만료를 제외한다`() {
        service.create(1) // 만료 = T0 + 3600 (뒤에서 만료되어 목록에서 빠질 세션)
        clock.advanceSeconds(1800)
        val live = service.create(1) // 만료 = T0 + 5400
        clock.advanceSeconds(1800) // now = T0 + 3600 → dead 만 만료
        val list = service.listActiveSessions()
        assertEquals(listOf(live.token), list.map { it.token })
    }

    @Test
    fun `listActiveSessions 정렬 - updatedAtMs 내림차순, 위치 미보고는 마지막`() {
        val recent = service.create(null)
        val older = service.create(null)
        val noLocation = service.create(null)
        clock.advanceSeconds(10)
        service.updateLocation(older.token, older.ownerSecret, report()) // updatedAt = T0 + 10
        clock.advanceSeconds(10)
        service.updateLocation(recent.token, recent.ownerSecret, report()) // updatedAt = T0 + 20
        val tokens = service.listActiveSessions().map { it.token }
        assertEquals(listOf(recent.token, older.token, noLocation.token), tokens)
        assertNull(service.listActiveSessions().last().updatedAtMs) // 위치 미보고는 마지막·updatedAtMs null
    }

    @Test
    fun `listActiveSessions 동률(위치 미보고 그룹)은 token 오름차순`() {
        val x = service.create(null)
        val y = service.create(null)
        val expected = listOf(x.token, y.token).sorted() // 둘 다 updatedAtMs null → token 오름차순
        assertEquals(expected, service.listActiveSessions().map { it.token })
    }

    @Test
    fun `listActiveSessions 응답 어디에도 owner_secret 이 없다`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report())
        val json = jacksonObjectMapper().writeValueAsString(service.listActiveSessions())
        assertFalse(json.contains(s.ownerSecret)) // 비밀 값 자체
        assertFalse(json.contains("ownerSecret"))
        assertFalse(json.contains("owner_secret"))
    }
}
