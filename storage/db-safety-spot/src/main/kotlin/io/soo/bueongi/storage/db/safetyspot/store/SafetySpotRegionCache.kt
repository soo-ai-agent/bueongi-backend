package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

// 외부 API 지역 조각의 재수집 주기 판정. 받은 지 ttlDays 안이면 다시 묻지 않는다(0건으로 받은 지역 포함).
@Component
class SafetySpotRegionCache(
    private val liveStore: SafetySpotLiveStore,
    @param:Value("\${bueongi.facility.region-cache-ttl-days:90}") private val ttlDays: Long,
    private val clock: Clock,
) {
    fun isCached(type: SafetySpotType, regionCode: String): Boolean {
        val savedAt: Instant = liveStore.savedAtOf(type, regionCode) ?: return false
        return Duration.between(savedAt, clock.instant()) < Duration.ofDays(ttlDays)
    }
}
