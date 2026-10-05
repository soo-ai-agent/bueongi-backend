package io.soo.bueongi.core.admin.ops.domain.externalfetch

import io.soo.bueongi.core.common.event.SafetySpotFetchPerformed
import io.soo.bueongi.core.enums.SafetySpotType
import org.springframework.stereotype.Component
import java.time.Clock

// 메모리 보관 — 서버를 재시작하면 0 부터 다시 센다.
@Component
class SafetySpotFetchLedger(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val byType: LinkedHashMap<SafetySpotType, SafetySpotFetchStatus> = LinkedHashMap()

    @Synchronized
    fun record(fetch: SafetySpotFetchPerformed) {
        val prior: SafetySpotFetchStatus? = byType[fetch.type]
        var successCount: Long = prior?.successCount ?: 0
        var failureCount: Long = prior?.failureCount ?: 0
        if (fetch.receivedCount == null) {
            failureCount++
        } else {
            successCount++
        }
        byType[fetch.type] =
            SafetySpotFetchStatus(
                type = fetch.type,
                successCount = successCount,
                failureCount = failureCount,
                lastAtMs = clock.millis(),
                lastScope = fetch.scope,
                lastReceivedCount = fetch.receivedCount,
            )
    }

    @Synchronized
    fun statuses(): List<SafetySpotFetchStatus> = ArrayList(byType.values)
}
