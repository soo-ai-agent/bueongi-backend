package io.soo.bueongi.domain.share

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 테스트용 가변 [Clock]. TTL/만료 경계를 sleep 없이 결정론적으로 검증하기 위해 시각을 임의로 전진시킨다.
 * 실시간 위치 공유는 시간이 동작의 핵심(1~3시간 상한 만료)이라, 실제 시계 대신 이 Clock 을 주입해
 * "정확히 1시간 후" 같은 경계를 재현 가능하게 만든다.
 */
class MutableTestClock(
    var now: Instant = Instant.parse("2026-06-19T12:00:00Z"),
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(z: ZoneId): Clock = MutableTestClock(now, z)

    /** 시각을 [seconds] 초 전진시킨다(만료/stale 경계 재현용). */
    fun advanceSeconds(seconds: Long) {
        now = now.plusSeconds(seconds)
    }
}
