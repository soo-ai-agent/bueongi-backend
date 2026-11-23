package io.soo.bueongi.core.share.domain.service

import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.core.enums.AlertCategory
import io.soo.bueongi.core.enums.ShareProgressStatus
import io.soo.bueongi.core.share.domain.model.ARRIVAL_RADIUS_METERS
import io.soo.bueongi.core.share.domain.model.progressStatus
import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.store.ShareSessionStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class MissedArrivalWatchdog(
    private val store: ShareSessionStore,
    private val clock: Clock,
    private val events: ApplicationEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(MissedArrivalWatchdog::class.java)

    // 같은 정지 상태가 이어지는 동안 스캔(60초)마다 다시 알리지 않는다 — 알림 이력이 세션 하나로 가득 찬다.
    // 위치가 다시 들어오면(IN_PROGRESS) 비워서, 다음 정지 때 또 알린다. @Scheduled 단일 스레드라 동기화 불요.
    private val alertedTokens: MutableSet<String> = HashSet()

    @Scheduled(fixedDelayString = "\${bueongi.share.watchdog-interval-ms:60000}")
    fun scan() {
        val now: Instant = clock.instant()
        val activeTokens: MutableSet<String> = HashSet()
        for (session in store.findActive()) {
            activeTokens.add(session.token)
            when (session.progressStatus(now)) {
                ShareProgressStatus.IN_PROGRESS -> alertedTokens.remove(session.token)
                ShareProgressStatus.ARRIVAL_LIKELY -> {
                    if (session.token !in alertedTokens) {
                        alertedTokens.add(session.token)
                        alertArrivalLikely(session, now)
                    }
                }
                ShareProgressStatus.MISSED_ARRIVAL_SUSPECT -> {
                    if (session.token !in alertedTokens) {
                        alertedTokens.add(session.token)
                        alertMissedArrival(session, now)
                    }
                }
            }
        }
        // 만료로 사라진 세션의 토큰을 비운다 — 안 비우면 집합이 계속 자란다.
        alertedTokens.retainAll(activeTokens)
    }

    private fun alertMissedArrival(session: ShareSession, now: Instant) {
        val expected: Instant = session.expectedArrivalAt ?: return
        val overdueMinutes: Long = Duration.between(expected, now).toMinutes()
        val lastReportedAt: Instant? = session.location?.updatedAt
        var stoppedText: String = "위치 보고 없음"
        if (lastReportedAt != null) {
            val stoppedSeconds: Long = Duration.between(lastReportedAt, now).seconds
            stoppedText = "위치가 ${stoppedSeconds}초째 정지"
        }
        val masked: String = maskToken(session.token)
        // masked 는 앞 6자만 남긴 값이어야 한다(PII 금지).
        events.publishEvent(
            OperationEvent(
                key = AlertCategory.MISSED_ARRIVAL.keyOf(masked),
                title = "미도착 의심 — 위치 공유",
                detail = "예정 도착 시간을 ${overdueMinutes}분 초과, $stoppedText. 관리자 페이지에서 확인하세요.",
            ),
        )
        log.warn("미도착 의심 감지 — token={} (예정 초과 {}분)", masked, overdueMinutes)
    }

    private fun alertArrivalLikely(session: ShareSession, now: Instant) {
        val expected: Instant = session.expectedArrivalAt ?: return
        val overdueMinutes: Long = Duration.between(expected, now).toMinutes()
        val masked: String = maskToken(session.token)
        // masked 는 앞 6자만 남긴 값이어야 한다(PII 금지).
        events.publishEvent(
            OperationEvent(
                key = AlertCategory.ARRIVAL_LIKELY.keyOf(masked),
                title = "도착 추정 — 위치 공유 미종료",
                detail = "예정 도착 시간을 ${overdueMinutes}분 초과했지만 마지막 위치가 목적지 반경 ${ARRIVAL_RADIUS_METERS}m 이내 — 도착 후 공유를 끝내지 않은 것으로 보입니다.",
            ),
        )
        log.info("도착 추정 감지 — token={} (예정 초과 {}분)", masked, overdueMinutes)
    }

    private fun maskToken(token: String): String {
        return token.take(6) + "…"
    }
}
