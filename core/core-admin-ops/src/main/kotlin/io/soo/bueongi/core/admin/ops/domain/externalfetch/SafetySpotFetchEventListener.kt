package io.soo.bueongi.core.admin.ops.domain.externalfetch

import io.soo.bueongi.core.common.event.SafetySpotFetchPerformed
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class SafetySpotFetchEventListener(
    private val ledger: SafetySpotFetchLedger,
) {
    @EventListener
    fun onFetchPerformed(event: SafetySpotFetchPerformed) {
        ledger.record(event)
    }
}
