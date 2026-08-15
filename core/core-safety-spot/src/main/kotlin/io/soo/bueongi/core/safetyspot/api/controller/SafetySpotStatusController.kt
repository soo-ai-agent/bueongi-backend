package io.soo.bueongi.core.safetyspot.api.controller

import io.soo.bueongi.core.safetyspot.domain.status.service.SafetySpotStatusService
import io.soo.bueongi.core.safetyspot.domain.status.model.SafetySpotStatusSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 경로가 /api/admin 아래인 것은 프론트 계약이다 — 시설 데이터의 현황이라 시설 모듈이 답한다. */
@RestController
class SafetySpotStatusController(
    private val safetySpotStatusService: SafetySpotStatusService,
) {

    @GetMapping("/api/admin/safety-spot-status")
    fun safetySpotStatus(): SafetySpotStatusSummary =
        safetySpotStatusService.summarize()
}
