package io.soo.bueongi.domain.share
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.storage.db.core.share.InMemoryLocationStore
import io.soo.bueongi.storage.db.core.share.StoredCoord
import io.soo.bueongi.storage.db.core.share.StoredMarker
import io.soo.bueongi.storage.db.core.share.StoredRoute
import io.soo.bueongi.storage.db.core.share.StoredRouteDestination

import io.soo.bueongi.support.error.ShareSessionNotFoundException
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

    private val clock: MutableTestClock = MutableTestClock()
    private val store: InMemoryLocationStore = InMemoryLocationStore(clock)
    private val service: ShareService = ShareService(store, clock, BASE_URL)

    /** 위치 보고(도메인 입력). 쓰기 비밀은 updateLocation 호출부에서 별도 인자로 넘긴다. */
    private fun report(lat: Double = 37.5, lng: Double = 127.0, remainingM: Double? = null, remainingS: Double? = null) =
        LocationReport(lat = lat, lng = lng, remainingM = remainingM, remainingS = remainingS)

    /** 경로 컨텍스트(저장 모델). 쓰기 비밀은 saveRoute 호출부에서 별도 인자로 넘긴다. */
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
        clock.advanceSeconds(60 + MISSED_ARRIVAL_GRACE_SECONDS + MISSED_ARRIVAL_OVERDUE_SECONDS + 1)
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
        assertEquals(50, tokens.toSet().size) // 충돌 없음
        tokens.forEach { t ->
            assertEquals(22, t.length) // 16바이트 base64url(패딩 제거) = 22자
            assertTrue(t.all { it.isLetterOrDigit() || it == '-' || it == '_' }) // URL-safe 문자만
        }
    }

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
        clock.advanceSeconds(3600) // 정확히 1시간 → 만료
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
        repeat(3) { service.getLocationForAdmin(s.token) } // 관리자 열람 반복
        assertFalse(store.isWatched(s.token)) // 보호자 시청으로 집계되지 않는다
    }

    @Test
    fun `대조 - 공개 getLocation 은 여전히 markViewed 를 호출한다(isWatched true, 회귀 없음)`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report())
        assertFalse(store.isWatched(s.token)) // 조회 전
        service.getLocation(s.token) // 보호자 조회 → 시청 기록
        assertTrue(store.isWatched(s.token))
    }

    @Test
    fun `만료된 공유의 getLocationForAdmin 은 expired=true 에 좌표 null`() {
        val s = service.create(1)
        service.updateLocation(s.token, s.ownerSecret, report())
        clock.advanceSeconds(3600) // 정확히 1시간 → 만료
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
        assertFalse(store.isWatched("nonexistent")) // 없는 토큰도 집계하지 않는다
    }

    @Test
    fun `생성됐으나 첫 위치 전이면 getLocationForAdmin 은 expired=false 에 좌표 null`() {
        val token = service.create(null).token
        val view = service.getLocationForAdmin(token)
        assertFalse(view.expired)
        assertNull(view.lat)
        assertNull(view.lng)
        assertNull(view.updatedAt)
        assertFalse(store.isWatched(token)) // 첫 위치 전에도 비집계
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
        assertEquals(37.1, service.getLocation(s.token).lat) // 종료되지 않음
    }

    @Test
    fun `없는 token end 는 멱등(no-op)`() {
        service.end("nonexistent", "x") // 예외 없이 통과
    }

    @Test
    fun `보호자 조회가 있으면 watching=true, 없으면 false`() {
        val s = service.create(null)
        assertFalse(service.getWatchingStatus(s.token, s.ownerSecret).watching)
        service.getLocation(s.token) // 보호자 조회 → 시청 기록
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
        // 경로 저장 후에도 위치 유지
        assertEquals(37.1, service.getLocation(s.token).lat)
        // 위치 갱신 후에도 경로 유지
        service.updateLocation(s.token, s.ownerSecret, report(37.2, 127.2))
        assertEquals("집", service.getRoute(s.token).route!!.destination.name)
    }

    @Test
    fun `remaining_m·remaining_s 는 위치 조회로 왕복된다`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report(remainingM = 500.0, remainingS = 360.0))
        val view = service.getLocation(s.token)
        assertEquals(500.0, view.remainingM)
        assertEquals(360.0, view.remainingS)
    }

    @Test
    fun `remaining 미보고 시 위치 조회의 remaining 은 null`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report())
        val view = service.getLocation(s.token)
        assertNull(view.remainingM)
        assertNull(view.remainingS)
    }

    @Test
    fun `watching 조회도 remaining 을 함께 돌려준다(관리자 미리보기 일관성)`() {
        val s = service.create(null)
        service.updateLocation(s.token, s.ownerSecret, report(remainingM = 250.0, remainingS = 120.0))
        val res = service.getWatchingStatus(s.token, s.ownerSecret)
        assertEquals(250.0, res.remainingM)
        assertEquals(120.0, res.remainingS)
    }

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

    private companion object {
        const val BASE_URL: String = "http://localhost:8119"
    }
}
