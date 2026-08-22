package io.soo.bueongi.core.safetyspot.api.controller

import io.soo.bueongi.core.safetyspot.api.response.RegionSafetySpotResponse
import io.soo.bueongi.core.safetyspot.api.response.RegionSafetySpotsResponse
import io.soo.bueongi.core.safetyspot.domain.status.model.RegionSafetySpotCount
import io.soo.bueongi.core.safetyspot.domain.status.service.RegionSafetySpotStatusService
import io.soo.bueongi.core.safetyspot.domain.status.service.SafetySpotStatusService
import io.soo.bueongi.core.safetyspot.domain.status.model.SafetySpotStatusSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 경로가 /api/admin 아래인 것은 프론트 계약이다 — 시설 데이터의 현황이라 시설 모듈이 답한다. */
@RestController
class SafetySpotStatusController(
    private val safetySpotStatusService: SafetySpotStatusService,
    private val regionStatusService: RegionSafetySpotStatusService,
) {

    @GetMapping("/api/admin/safety-spot-status")
    fun safetySpotStatus(): SafetySpotStatusSummary =
        safetySpotStatusService.summarize()

    @GetMapping("/api/admin/safety-spot-status/regions")
    fun regions(): List<RegionSafetySpotResponse> {
        val counts: List<RegionSafetySpotCount> = regionStatusService.summarize()
        val responses: ArrayList<RegionSafetySpotResponse> = ArrayList(counts.size)
        for (count in counts) {
            responses.add(RegionSafetySpotResponse.from(count))
        }
        return responses
    }

    @GetMapping("/api/admin/safety-spot-status/regions/{code}/spots")
    fun regionSpots(
        @PathVariable code: String,
        @RequestParam(required = false, defaultValue = "2000") limit: Int,
    ): RegionSafetySpotsResponse =
        RegionSafetySpotsResponse.from(regionStatusService.findSpotsByCode(code, limit))
}
