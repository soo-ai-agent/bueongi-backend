package io.soo.bueongi.core.admin.ops.api.controller

import io.soo.bueongi.core.admin.ops.api.response.SafetySpotFetchStatusResponse
import io.soo.bueongi.core.admin.ops.domain.externalfetch.SafetySpotFetchLedger
import io.soo.bueongi.core.admin.ops.domain.externalfetch.SafetySpotFetchStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/safety-spot-fetches")
class SafetySpotFetchController(
    private val ledger: SafetySpotFetchLedger,
) {
    @GetMapping
    fun fetchStatuses(): List<SafetySpotFetchStatusResponse> {
        val statuses: List<SafetySpotFetchStatus> = ledger.statuses()
        val responses: ArrayList<SafetySpotFetchStatusResponse> = ArrayList(statuses.size)
        for (status in statuses) {
            responses.add(SafetySpotFetchStatusResponse.from(status))
        }
        return responses
    }
}
