package io.soo.bueongi.core.admin.ops.api.controller

import io.soo.bueongi.core.admin.ops.api.response.SafetySpotFetchStatusResponse
import io.soo.bueongi.core.admin.ops.domain.externalfetch.SafetySpotFetchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/safety-spot-fetches")
class SafetySpotFetchController(
    private val safetySpotFetchService: SafetySpotFetchService,
) {
    @GetMapping
    fun fetchStatuses(): List<SafetySpotFetchStatusResponse> {
        return safetySpotFetchService.findStatuses()
    }
}
