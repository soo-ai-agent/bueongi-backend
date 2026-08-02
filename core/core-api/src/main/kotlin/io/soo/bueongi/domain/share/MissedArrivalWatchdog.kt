package io.soo.bueongi.domain.share

import io.soo.bueongi.storage.db.core.share.ShareSession
import io.soo.bueongi.storage.db.core.share.StoredLocation
import io.soo.bueongi.storage.db.core.share.LocationStore
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * 미도착 감시(서버측 안전망) — 예정 도착 시각을 여유 넘겨 초과했는데 위치 전송까지 멈춘 활성 공유를
 * 주기적으로 찾아 운영자에게 알린다. 폰 방전·네트워크 두절·앱 종료처럼 클라이언트가 스스로 못 알리는
 * 상황을 서버가 대신 감지한다(E 클라 포그라운드 체크인의 서버측 복제).
 *
 * 프라이버시: 이 앱은 PII(보호자 연락처)를 저장하지 않으므로 보호자에게 직접 알릴 수 없다 —
 * 알림은 운영자 Slack 으로만 가고, 보호자에게는 그가 이미 연 페이지의 `overdue` 격상으로 전달된다(별도 FE).
 * 판정은 **읽기 전용**이다 — 세션 상태를 바꾸지 않는다(시청 집계·TTL 오염 금지). 원문 토큰·좌표는 알림에 싣지 않는다.
 */
@Component
class MissedArrivalWatchdog(
    private val store: LocationStore,
    private val clock: Clock,
    /** 운영 알림(없으면 null 무해) — @WebMvcTest 등 슬라이스엔 알림 빈이 없어 nullable 로 받는다. */
    private val alertNotifier: AlertNotifier? = null,
) {
    private val log: Logger = LoggerFactory.getLogger(MissedArrivalWatchdog::class.java)

    /** 주기 스캔(기본 60초). 활성 세션 중 미도착 의심을 찾아 운영자 Slack 으로 알린다(스로틀은 AlertNotifier). */
    @Scheduled(fixedDelayString = "\${bueongi.share.watchdog-interval-ms:60000}")
    fun scan() {
        val now: Instant = clock.instant()
        store.findActiveSessions()
            .filter { isMissedArrivalSuspect(it, now) }
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
        /** 원문 토큰(위치 읽기 자격증명)을 알림/로그에 노출하지 않도록 앞 6자만 남긴다. */
        fun maskToken(token: String): String = token.take(6) + "…"
    }
}

/**
 * 미도착 의심 판정(순수) — 예정 도착 시각을 여유([MISSED_ARRIVAL_OVERDUE_SECONDS]) 넘겨 초과 + 위치 전송이 멈춤.
 * ETA 미제공(expectedArrivalAt=null) 세션은 제외한다. "늦지만 이동 중"(위치가 계속 갱신)은 위치가 안 멈췄으니 제외된다.
 */
fun isMissedArrivalSuspect(session: ShareSession, now: Instant): Boolean {
    val expected: Instant = session.expectedArrivalAt ?: return false
    val overdue: Boolean = now.isAfter(expected.plusSeconds(MISSED_ARRIVAL_OVERDUE_SECONDS))
    return overdue && isLocationStopped(session, now)
}

/** 위치 전송이 멈췄는지(순수) — 한 번도 안 보냈거나(null) 마지막 위치가 [MISSED_ARRIVAL_STOPPED_SECONDS] 보다 오래됨. */
fun isLocationStopped(session: ShareSession, now: Instant): Boolean {
    val location: StoredLocation = session.location ?: return true
    return now.isAfter(location.updatedAt.plusSeconds(MISSED_ARRIVAL_STOPPED_SECONDS))
}
