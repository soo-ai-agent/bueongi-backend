package io.soo.bueongi.core.safetyspot.domain.fill.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

// 전국 한 덩이로 받는 시설 채움의 공통 흐름. 자식은 무엇을(type) 어떻게 받아(fetch)
// 어떤 필드로 바꾸는지(toSafetySpots)만 정한다.
abstract class NationwideFillService<T>(
    private val cache: SafetySpotRegionCache,
    private val liveStore: SafetySpotLiveStore,
    private val retryGate: FillFailureCooldown,
    private val events: SafetySpotFillEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val inFlight: AtomicBoolean = AtomicBoolean(false)

    protected abstract val type: SafetySpotType

    protected abstract val isSourceEnabled: Boolean

    protected abstract fun fetch(): List<T>?

    protected abstract fun toSafetySpots(fetched: List<T>): List<SafetySpotPoint>

    fun isFilled(): Boolean = cache.isCached(type, NATIONWIDE)

    fun fill(): Int {
        if (!isSourceEnabled) {
            return 0
        }
        if (isFilled()) {
            return 0
        }
        if (retryGate.isCoolingDown(NATIONWIDE)) {
            return 0
        }
        if (!inFlight.compareAndSet(false, true)) {
            return 0
        }
        return try {
            fetchAndStore()
        } finally {
            inFlight.set(false)
        }
    }

    private fun fetchAndStore(): Int {
        val fetched: List<T>? = fetch()
        if (fetched == null) {
            retryGate.recordFailure(NATIONWIDE)
            events.fetchFailed(type, NATIONWIDE)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        if (points.isEmpty()) {
            cache.save(type, NATIONWIDE, points)
            events.hadNoCoordinates(type, NATIONWIDE, fetched.size)
            return 0
        }
        cache.save(type, NATIONWIDE, points)
        liveStore.append(type, points)
        log.info("{} 채움: 받은={}, 좌표있음={}", type.label, fetched.size, points.size)
        return points.size
    }
}
