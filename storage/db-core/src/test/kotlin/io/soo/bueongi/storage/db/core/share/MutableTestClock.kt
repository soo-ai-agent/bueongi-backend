package io.soo.bueongi.storage.db.core.share

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableTestClock(
    var now: Instant = Instant.parse("2026-06-19T12:00:00Z"),
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = zone

    override fun withZone(z: ZoneId): Clock = MutableTestClock(now, z)

    fun advanceSeconds(seconds: Long) {
        now = now.plusSeconds(seconds)
    }
}
