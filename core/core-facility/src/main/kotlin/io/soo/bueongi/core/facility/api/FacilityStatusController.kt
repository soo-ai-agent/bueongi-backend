package io.soo.bueongi.core.facility.api

import io.soo.bueongi.core.facility.domain.status.service.FacilityStatusService
import io.soo.bueongi.core.facility.domain.status.model.FacilityStatusSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 경로가 /api/admin 아래인 것은 프론트 계약이다 — 시설 데이터의 현황이라 시설 모듈이 답한다. */
@RestController
class FacilityStatusController(
    private val facilityStatusService: FacilityStatusService,
) {

    @GetMapping("/api/admin/facility-status")
    fun facilityStatus(): FacilityStatusSummary = facilityStatusService.summarize()
}
