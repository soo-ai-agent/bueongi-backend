package io.soo.bueongi.core.admin.safetyspot.api.controller

import io.soo.bueongi.core.admin.safetyspot.api.response.RegionSafetySpotResponse
import io.soo.bueongi.core.admin.safetyspot.api.response.RegionSafetySpotsResponse
import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotStatusResponse
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.RegionSafetySpotStatusService
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.SafetySpotStatusService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class SafetySpotStatusController(
    private val safetySpotStatusService: SafetySpotStatusService,
    private val regionStatusService: RegionSafetySpotStatusService,
) {
    @GetMapping("/api/admin/safety-spot-status")
    fun find(): SafetySpotStatusResponse {
        return safetySpotStatusService.summarize()
    }

    @GetMapping("/api/admin/safety-spot-status/regions")
    fun regions(): List<RegionSafetySpotResponse> {
        return regionStatusService.summarize()
    }

    @GetMapping("/api/admin/safety-spot-status/regions/{code}/spots")
    fun regionSpots(
        @PathVariable code: String,
        @RequestParam(required = false, defaultValue = "2000") limit: Int,
    ): RegionSafetySpotsResponse {
        return regionStatusService.findByCode(code, limit)
    }
}
