package io.soo.bueongi.core.admin.ops.api.controller

import io.soo.bueongi.core.admin.ops.api.response.SafetySpotFetchStatusResponse
import io.soo.bueongi.core.admin.ops.domain.externalfetch.SafetySpotFetchStatusService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/safety-spot-fetches")
class SafetySpotFetchStatusController(
    private val safetySpotFetchStatusService: SafetySpotFetchStatusService,
) {
    @GetMapping
    fun findAll(): List<SafetySpotFetchStatusResponse> {
        return safetySpotFetchStatusService.findAll()
    }
}
