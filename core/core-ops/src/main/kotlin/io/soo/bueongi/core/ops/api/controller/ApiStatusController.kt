package io.soo.bueongi.core.ops.api.controller

import io.soo.bueongi.core.ops.api.response.ApiCallLogResponse
import io.soo.bueongi.core.ops.domain.apistatus.service.ApiStatusService
import io.soo.bueongi.core.ops.domain.apistatus.model.ApiStatusSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 외부 API 호출 건강도. 경로가 /api/admin 아래인 것은 프론트 계약이다. */
@RestController
@RequestMapping("/api/admin")
class ApiStatusController(
    private val apiStatusService: ApiStatusService,
) {

    @GetMapping("/api-status")
    fun apiStatus(): ApiStatusSummary = apiStatusService.summarize()

    @GetMapping("/api-logs")
    fun apiLogs(
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
    ): List<ApiCallLogResponse> =
        apiStatusService.listRecentCalls(provider, limit).map { ApiCallLogResponse.from(it) }
}
