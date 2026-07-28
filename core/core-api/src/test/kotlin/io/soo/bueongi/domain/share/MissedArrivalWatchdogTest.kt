package io.soo.bueongi.domain.share

import io.soo.bueongi.storage.db.core.share.LocationStore
import io.soo.bueongi.storage.db.core.share.ShareSession
import io.soo.bueongi.storage.db.core.share.StoredLocation
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * 미도착 감시 판정(순수)과 스캔→알림 배선. 결정론적 now 로 경계를 고정한다:
 * ETA 없음 / 예정 초과 전 / 늦지만 이동 중 → 제외, 예정 초과 + 위치 정지·미보고 → 의심.
 * 알림은 토큰 마스킹·좌표 비노출을 지킨다(프라이버시).
 */
class MissedArrivalWatchdogTest {

    private val now = Instant.parse("2026-07-28T12:00:00Z")

    private fun session(expectedArrivalAt: Instant?, lastLocationAt: Instant? = null): ShareSession =
        ShareSession(
            token = "tok-abcdef-123456",
            ownerSecret = "sek",
            createdAt = now.minusSeconds(3600),
            expiresAt = now.plusSeconds(3600),
            location = lastLocationAt?.let { StoredLocation(lat = 37.5, lng = 127.0, updatedAt = it) },
            expectedArrivalAt = expectedArrivalAt,
        )

    @Test
    fun `ETA 미제공 세션은 의심하지 않는다`() {
        assertThat(isMissedArrivalSuspect(session(expectedArrivalAt = null), now)).isFalse()
    }

    @Test
    fun `예정 초과 전(여유 이내)이면 의심하지 않는다`() {
        val stillMoving = session(expectedArrivalAt = now.plusSeconds(60), lastLocationAt = now.minusSeconds(300))
        assertThat(isMissedArrivalSuspect(stillMoving, now)).isFalse()
    }

    @Test
    fun `예정 초과했으나 위치가 계속 갱신 중이면(늦지만 이동 중) 의심하지 않는다`() {
        val expected = now.minusSeconds(MISSED_ARRIVAL_OVERDUE_SECONDS + 60) // 예정 초과
        val movingLate = session(expectedArrivalAt = expected, lastLocationAt = now.minusSeconds(10)) // 위치 최신
        assertThat(isMissedArrivalSuspect(movingLate, now)).isFalse()
    }

    @Test
    fun `예정 초과 + 위치 정지면 의심한다`() {
        val expected = now.minusSeconds(MISSED_ARRIVAL_OVERDUE_SECONDS + 60)
        val stopped = session(
            expectedArrivalAt = expected,
            lastLocationAt = now.minusSeconds(MISSED_ARRIVAL_STOPPED_SECONDS + 30),
        )
        assertThat(isMissedArrivalSuspect(stopped, now)).isTrue()
    }

    @Test
    fun `예정 초과 + 위치 한 번도 없음도 의심한다`() {
        val expected = now.minusSeconds(MISSED_ARRIVAL_OVERDUE_SECONDS + 60)
        assertThat(isMissedArrivalSuspect(session(expectedArrivalAt = expected, lastLocationAt = null), now)).isTrue()
    }

    @Test
    fun `scan 은 의심 세션만 운영자에게 알린다 — 토큰 마스킹·좌표 비노출`() {
        val suspect = session(
            expectedArrivalAt = now.minusSeconds(MISSED_ARRIVAL_OVERDUE_SECONDS + 120),
            lastLocationAt = now.minusSeconds(MISSED_ARRIVAL_STOPPED_SECONDS + 60),
        )
        val healthy = session(expectedArrivalAt = now.plusSeconds(600), lastLocationAt = now.minusSeconds(5))
        val notifier: RecordingNotifier = RecordingNotifier()
        val watchdog = MissedArrivalWatchdog(fixedStore(listOf(suspect, healthy)), Clock.fixed(now, ZoneOffset.UTC), notifier)

        watchdog.scan()

        assertThat(notifier.notified).hasSize(1)
        val (key, title, detail) = notifier.notified[0]
        assertThat(key).isEqualTo("missed-arrival:tok-ab…")
        assertThat(title).contains("미도착")
        // 원문 토큰·좌표는 알림 본문에 싣지 않는다(프라이버시).
        assertThat(detail).doesNotContain(suspect.token)
        assertThat(detail).doesNotContain("37.5")
    }

    private fun fixedStore(active: List<ShareSession>): LocationStore = object : LocationStore {
        override val backend: String = "memory"
        override fun save(session: ShareSession) = Unit
        override fun findByToken(token: String): ShareSession? = null
        override fun deleteByToken(token: String) = Unit
        override fun markViewed(token: String) = Unit
        override fun isWatched(token: String): Boolean = false
        override fun countActiveSessions(): Int = active.size
        override fun findActiveSessions(): List<ShareSession> = active
    }

    private class RecordingNotifier : AlertNotifier {
        val notified = ArrayList<Triple<String, String, String>>()
        override val isEnabled: Boolean = true
        override fun notify(key: String, title: String, detail: String) {
            notified.add(Triple(key, title, detail))
        }
    }
}
