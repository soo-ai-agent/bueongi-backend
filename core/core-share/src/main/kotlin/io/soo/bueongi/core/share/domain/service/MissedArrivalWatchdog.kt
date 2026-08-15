package io.soo.bueongi.core.share.domain.service

import io.soo.bueongi.support.monitoring.AlertNotifier

import io.soo.bueongi.storage.db.core.share.ShareSession
import io.soo.bueongi.storage.db.core.share.LocationStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class MissedArrivalWatchdog(
    private val store: LocationStore,
    private val clock: Clock,
    private val alertNotifier: AlertNotifier? = null,
) {
    private val log: Logger = LoggerFactory.getLogger(MissedArrivalWatchdog::class.java)

    @Scheduled(fixedDelayString = "\${bueongi.share.watchdog-interval-ms:60000}")
    fun scan() {
        val now: Instant = clock.instant()
        store.findActiveSessions()
            .filter { it.isMissedArrivalSuspect(now) }
            .forEach { alert(it, now) }
    }

    private fun alert(session: ShareSession, now: Instant) {
        val expected: Instant = session.expectedArrivalAt ?: return
        val overdueMinutes: Long = Duration.between(expected, now).toMinutes()
        val stoppedSeconds: Long? = session.location?.updatedAt?.let { Duration.between(it, now).seconds }
        val stoppedText: String = stoppedSeconds?.let { "위치가 ${it}초째 정지" } ?: "위치 보고 없음"
        val masked: String = maskToken(session.token)
        alertNotifier?.notify(
            key = "missed-arrival:$masked",
            title = "미도착 의심 — 위치 공유",
            detail = "예정 도착 시간을 ${overdueMinutes}분 초과, $stoppedText. 관리자 페이지에서 확인하세요.",
        )
        log.warn("미도착 의심 감지 — token={} (예정 초과 {}분)", masked, overdueMinutes)
    }

    private companion object {
        fun maskToken(token: String): String = token.take(6) + "…"
    }
}

