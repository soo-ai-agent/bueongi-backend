package io.soo.bueongi.core.admin.ops.api.controller

import io.soo.bueongi.core.admin.ops.api.response.AlertsResponse
import io.soo.bueongi.core.admin.ops.domain.alert.AlertHistoryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/alerts")
class AlertController(
    private val alertHistoryService: AlertHistoryService,
) {
    @GetMapping
    fun findRecent(
        @RequestParam(required = false, defaultValue = "100") limit: Int,
    ): AlertsResponse {
        return alertHistoryService.findRecent(limit)
    }
}
