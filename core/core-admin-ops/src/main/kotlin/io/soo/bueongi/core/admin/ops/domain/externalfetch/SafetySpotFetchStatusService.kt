package io.soo.bueongi.core.admin.ops.domain.externalfetch

import io.soo.bueongi.core.admin.ops.api.response.SafetySpotFetchStatusResponse
import org.springframework.stereotype.Service

@Service
class SafetySpotFetchStatusService(
    private val ledger: SafetySpotFetchLedger,
) {
    fun findAll(): List<SafetySpotFetchStatusResponse> {
        val statuses: List<SafetySpotFetchStatus> = ledger.findAll()
        return SafetySpotFetchStatusResponse.from(statuses)
    }
}
