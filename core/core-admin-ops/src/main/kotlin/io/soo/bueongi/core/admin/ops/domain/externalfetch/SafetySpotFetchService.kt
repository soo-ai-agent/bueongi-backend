package io.soo.bueongi.core.admin.ops.domain.externalfetch

import io.soo.bueongi.core.admin.ops.api.response.SafetySpotFetchStatusResponse
import org.springframework.stereotype.Service

@Service
class SafetySpotFetchService(
    private val ledger: SafetySpotFetchLedger,
) {
    fun findStatuses(): List<SafetySpotFetchStatusResponse> {
        val statuses: List<SafetySpotFetchStatus> = ledger.statuses()
        return SafetySpotFetchStatusResponse.from(statuses)
    }
}
