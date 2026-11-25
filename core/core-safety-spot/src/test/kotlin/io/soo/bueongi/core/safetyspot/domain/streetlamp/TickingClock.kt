package io.soo.bueongi.core.safetyspot.domain.streetlamp

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal class TickingClock(
    var now: Instant,
) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneOffset = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId): Clock = this
}
