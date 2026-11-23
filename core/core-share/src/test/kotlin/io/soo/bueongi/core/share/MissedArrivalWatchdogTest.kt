package io.soo.bueongi.core.share

import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.core.enums.ShareProgressStatus
import io.soo.bueongi.core.share.domain.model.LOCATION_STOPPED_SECONDS
import io.soo.bueongi.core.share.domain.model.OVERDUE_SECONDS
import io.soo.bueongi.core.share.domain.model.isLocationStopped
import io.soo.bueongi.core.share.domain.model.isMissedArrivalSuspect
import io.soo.bueongi.core.share.domain.model.isOverdue
import io.soo.bueongi.core.share.domain.model.progressStatus
import io.soo.bueongi.core.share.domain.service.MissedArrivalWatchdog
import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredLocation
import io.soo.bueongi.storage.db.share.model.StoredRoute
import io.soo.bueongi.storage.db.share.model.StoredRouteDestination
import io.soo.bueongi.storage.db.share.store.ShareSessionStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MissedArrivalWatchdogTest {
    private val now = Instant.parse("2026-07-28T12:00:00Z")

    private fun session(
        expectedArrivalAt: Instant?,
        lastLocationAt: Instant? = null,
        destination: StoredRouteDestination? = null,
    ): ShareSession =
        ShareSession(
            token = "tok-abcdef-123456",
            ownerSecret = "sek",
            createdAt = now.minusSeconds(3600),
            expiresAt = now.plusSeconds(3600),
            location = lastLocationAt?.let { StoredLocation(lat = 37.5, lng = 127.0, updatedAt = it) },
            route =
                destination?.let {
                    StoredRoute(
                        destination = it,
                        path = emptyList(),
                        markers = emptyList(),
                        totalDistanceMeters = 0,
                        totalTimeSeconds = 0,
                        type = "safe",
                    )
                },
            expectedArrivalAt = expectedArrivalAt,
        )

    @Test
    fun `ETA 미제공 세션은 의심하지 않는다`() {
        assertThat(session(expectedArrivalAt = null).isMissedArrivalSuspect(now)).isFalse()
    }

    @Test
    fun `예정 초과 전(여유 이내)이면 의심하지 않는다`() {
        val stillMoving = session(expectedArrivalAt = now.plusSeconds(60), lastLocationAt = now.minusSeconds(300))
        assertThat(stillMoving.isMissedArrivalSuspect(now)).isFalse()
    }

    @Test
    fun `예정 초과했으나 위치가 계속 갱신 중이면(늦지만 이동 중) 의심하지 않는다`() {
        val expected = now.minusSeconds(OVERDUE_SECONDS + 60)
        val movingLate = session(expectedArrivalAt = expected, lastLocationAt = now.minusSeconds(10))
        assertThat(movingLate.isMissedArrivalSuspect(now)).isFalse()
    }

    @Test
    fun `예정 초과 + 위치 정지면 의심한다`() {
        val expected = now.minusSeconds(OVERDUE_SECONDS + 60)
        val stopped =
            session(
                expectedArrivalAt = expected,
                lastLocationAt = now.minusSeconds(LOCATION_STOPPED_SECONDS + 30),
            )
        assertThat(stopped.isMissedArrivalSuspect(now)).isTrue()
    }

    @Test
    fun `예정 초과 + 위치 한 번도 없음도 의심한다`() {
        val expected = now.minusSeconds(OVERDUE_SECONDS + 60)
        assertThat(session(expectedArrivalAt = expected, lastLocationAt = null).isMissedArrivalSuspect(now)).isTrue()
    }

    @Test
    fun `예정 초과 + 정지라도 마지막 위치가 목적지 근처면 도착 추정이다`() {
        val arrivedButNotEnded =
            session(
                expectedArrivalAt = now.minusSeconds(OVERDUE_SECONDS + 60),
                lastLocationAt = now.minusSeconds(LOCATION_STOPPED_SECONDS + 30),
                destination = StoredRouteDestination(name = "집", lat = 37.5, lng = 127.0),
            )

        assertThat(arrivedButNotEnded.progressStatus(now)).isEqualTo(ShareProgressStatus.ARRIVAL_LIKELY)
    }

    @Test
    fun `목적지에서 먼 곳에 정지했으면 미도착 의심이다`() {
        // 위도 0.01도 차이 ≈ 1.1km — 도착 반경(30m) 밖.
        val stoppedFarAway =
            session(
                expectedArrivalAt = now.minusSeconds(OVERDUE_SECONDS + 60),
                lastLocationAt = now.minusSeconds(LOCATION_STOPPED_SECONDS + 30),
                destination = StoredRouteDestination(name = "집", lat = 37.51, lng = 127.0),
            )

        assertThat(stoppedFarAway.progressStatus(now)).isEqualTo(ShareProgressStatus.MISSED_ARRIVAL_SUSPECT)
    }

    @Test
    fun `경로 미등록이면 목적지를 몰라 미도착 의심으로 본다`() {
        val withoutRoute =
            session(
                expectedArrivalAt = now.minusSeconds(OVERDUE_SECONDS + 60),
                lastLocationAt = now.minusSeconds(LOCATION_STOPPED_SECONDS + 30),
            )

        assertThat(withoutRoute.progressStatus(now)).isEqualTo(ShareProgressStatus.MISSED_ARRIVAL_SUSPECT)
    }

    @Test
    fun `같은 정지 상태가 이어지면 scan 을 반복해도 한 번만 알린다`() {
        val suspect =
            session(
                expectedArrivalAt = now.minusSeconds(OVERDUE_SECONDS + 120),
                lastLocationAt = now.minusSeconds(LOCATION_STOPPED_SECONDS + 60),
            )
        val notifier = RecordingPublisher()
        val watchdog =
            MissedArrivalWatchdog(fixedStore(listOf(suspect)), Clock.fixed(now, ZoneOffset.UTC), notifier)

        watchdog.scan()
        watchdog.scan()

        assertThat(notifier.events).hasSize(1)
    }

    @Test
    fun `위치가 다시 들어왔다가 또 멈추면 다시 알린다`() {
        val suspect =
            session(
                expectedArrivalAt = now.minusSeconds(OVERDUE_SECONDS + 120),
                lastLocationAt = now.minusSeconds(LOCATION_STOPPED_SECONDS + 60),
            )
        val movingAgain = session(expectedArrivalAt = suspect.expectedArrivalAt, lastLocationAt = now.minusSeconds(5))
        val sessions: ArrayList<ShareSession> = ArrayList()
        sessions.add(suspect)
        val notifier = RecordingPublisher()
        val watchdog =
            MissedArrivalWatchdog(fixedStore(sessions), Clock.fixed(now, ZoneOffset.UTC), notifier)

        watchdog.scan()
        sessions[0] = movingAgain
        watchdog.scan()
        sessions[0] = suspect
        watchdog.scan()

        assertThat(notifier.events).hasSize(2)
    }

    @Test
    fun `scan 은 목적지 근처 정지를 도착 추정으로 알린다 — key 가 arrival-likely`() {
        val arrivedButNotEnded =
            session(
                expectedArrivalAt = now.minusSeconds(OVERDUE_SECONDS + 120),
                lastLocationAt = now.minusSeconds(LOCATION_STOPPED_SECONDS + 60),
                destination = StoredRouteDestination(name = "집", lat = 37.5, lng = 127.0),
            )
        val notifier = RecordingPublisher()
        val watchdog =
            MissedArrivalWatchdog(fixedStore(listOf(arrivedButNotEnded)), Clock.fixed(now, ZoneOffset.UTC), notifier)

        watchdog.scan()

        assertThat(notifier.events).hasSize(1)
        val event: OperationEvent = notifier.events[0] as OperationEvent
        assertThat(event.key).isEqualTo("arrival-likely:tok-ab…")
        assertThat(event.title).contains("도착 추정")
        assertThat(event.detail).doesNotContain(arrivedButNotEnded.token)
    }

    @Test
    fun `scan 은 의심 세션만 운영자에게 알린다 — 토큰 마스킹·좌표 비노출`() {
        val suspect =
            session(
                expectedArrivalAt = now.minusSeconds(OVERDUE_SECONDS + 120),
                lastLocationAt = now.minusSeconds(LOCATION_STOPPED_SECONDS + 60),
            )
        val healthy = session(expectedArrivalAt = now.plusSeconds(600), lastLocationAt = now.minusSeconds(5))
        val notifier = RecordingPublisher()
        val watchdog =
            MissedArrivalWatchdog(fixedStore(listOf(suspect, healthy)), Clock.fixed(now, ZoneOffset.UTC), notifier)

        watchdog.scan()

        assertThat(notifier.events).hasSize(1)
        val event: OperationEvent = notifier.events[0] as OperationEvent
        val key: String = event.key
        val title: String = event.title
        val detail: String = event.detail
        assertThat(key).isEqualTo("missed-arrival:tok-ab…")
        assertThat(title).contains("미도착")
        assertThat(detail).doesNotContain(suspect.token)
        assertThat(detail).doesNotContain("37.5")
    }

    private fun fixedStore(active: List<ShareSession>): ShareSessionStore =
        object : ShareSessionStore {
            override val backend: String = "memory"

            override fun save(session: ShareSession) = Unit

            override fun findByToken(token: String): ShareSession? = null

            override fun deleteByToken(token: String) = Unit

            override fun markViewed(token: String) = Unit

            override fun isWatched(token: String): Boolean = false

            override fun countActive(): Int = active.size

            override fun findActive(): List<ShareSession> = active
        }

    private class RecordingPublisher : ApplicationEventPublisher {
        val events: MutableList<Any> = ArrayList()

        override fun publishEvent(event: Any) {
            events.add(event)
        }
    }

    @Test
    fun `초과 임계값은 5분이다 - 경계 전후를 절대값으로 고정`() {
        val stopped: Instant = now.minusSeconds(10_000)

        val at299 = session(expectedArrivalAt = now.minusSeconds(299), lastLocationAt = stopped)
        val at301 = session(expectedArrivalAt = now.minusSeconds(301), lastLocationAt = stopped)

        assertThat(at299.isOverdue(now)).isFalse()
        assertThat(at301.isOverdue(now)).isTrue()
    }

    @Test
    fun `위치 정지 임계값은 90초다 - 경계 전후를 절대값으로 고정`() {
        val overdue: Instant = now.minusSeconds(10_000)

        val at89 = session(expectedArrivalAt = overdue, lastLocationAt = now.minusSeconds(89))
        val at91 = session(expectedArrivalAt = overdue, lastLocationAt = now.minusSeconds(91))

        assertThat(at89.isLocationStopped(now)).isFalse()
        assertThat(at91.isLocationStopped(now)).isTrue()
    }
}
