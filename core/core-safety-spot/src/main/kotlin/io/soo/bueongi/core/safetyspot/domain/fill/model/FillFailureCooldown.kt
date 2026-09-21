package io.soo.bueongi.core.safetyspot.domain.fill.model

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

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
        val DEFAULT_COOLDOWN: Duration = Duration.ofMinutes(10)
    }
}
