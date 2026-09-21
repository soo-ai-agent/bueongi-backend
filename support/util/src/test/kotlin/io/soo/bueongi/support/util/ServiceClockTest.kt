package io.soo.bueongi.support.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ServiceClockTest {
    private fun ms(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `오늘의 시작은 한국 자정이다`() {
        // 2026-08-15 00:00 KST = 2026-08-14 15:00 UTC
        assertThat(todayStartMs(ms("2026-08-15T03:00:00Z"))).isEqualTo(ms("2026-08-14T15:00:00Z"))
    }

    @Test
    fun `UTC 자정 직후는 아직 한국의 같은 날이다`() {
        // UTC 로 날이 바뀐 직후(09:00 KST)라도 "오늘"은 그날 한국 자정부터다.
        val justAfterUtcMidnight: Long = ms("2026-08-15T00:00:01Z")

        assertThat(todayStartMs(justAfterUtcMidnight)).isEqualTo(ms("2026-08-14T15:00:00Z"))
    }

    @Test
    fun `한국 자정 직전과 직후는 다른 날로 갈린다`() {
        val beforeMidnight: Long = ms("2026-08-14T14:59:59Z")
        val afterMidnight: Long = ms("2026-08-14T15:00:00Z")

        assertThat(todayStartMs(beforeMidnight)).isEqualTo(ms("2026-08-13T15:00:00Z"))
        assertThat(todayStartMs(afterMidnight)).isEqualTo(ms("2026-08-14T15:00:00Z"))
    }
}
