package io.soo.bueongi.core.safetyspot.domain.police

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal class TickingClock(
    var now: Instant,
) : Clock() {
    override fun instant(): Instant {
        return now
    }

    override fun getZone(): ZoneOffset {
        return ZoneOffset.UTC
    }

    override fun withZone(zone: ZoneId): Clock {
        return this
    }
}
