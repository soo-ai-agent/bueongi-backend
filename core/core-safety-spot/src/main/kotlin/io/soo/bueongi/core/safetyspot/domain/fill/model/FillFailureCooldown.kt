package io.soo.bueongi.core.safetyspot.domain.fill.model

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 실패한 수집의 재시도 간격을 벌린다.
 *
 * 채우기는 경로 요청마다 트리거되므로, 원천이 죽었거나 일일 조회 한도가 끝난 날에는
 * **요청이 올 때마다 실패 호출이 반복**된다. 같은 열쇠의 실패는 이 간격 안에서 다시
 * 시도하지 않는다 — 성공하면 캐시가 막아 주니 여기는 실패만 기억하면 된다.
 */
class FillFailureCooldown(
    private val cooldown: Duration = DEFAULT_COOLDOWN,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val failedAt: ConcurrentHashMap<String, Instant> = ConcurrentHashMap()

    fun isCoolingDown(key: String): Boolean {
        val at: Instant = failedAt[key] ?: return false
        return Duration.between(at, clock.instant()) < cooldown
    }

    fun recordFailure(key: String) {
        failedAt[key] = clock.instant()
    }

    private companion object {
        /** Slack 알림 스로틀과 같은 크기 — 장애 중 폭주를 막되 복구를 너무 늦게 알지 않는 선. */
        val DEFAULT_COOLDOWN: Duration = Duration.ofMinutes(10)
    }
}
