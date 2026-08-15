package io.soo.bueongi.core.ops.api

import io.soo.bueongi.core.ops.domain.slack.service.SlackConfigService
import io.soo.bueongi.core.ops.domain.slack.model.SlackConfigSummary
import io.soo.bueongi.core.ops.domain.slack.model.SlackTestResult
import io.soo.bueongi.support.monitoring.AlertNotifier
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 운영 알림 — 이력 조회와 Slack 설정. 경로가 /api/admin 아래인 것은 프론트 계약이다. */
@RestController
@RequestMapping("/api/admin")
class AlertController(
    private val alertNotifier: AlertNotifier,
    private val slackConfigService: SlackConfigService,
) {

    @GetMapping("/alerts")
    fun alerts(
        @RequestParam(required = false, defaultValue = "100") limit: Int,
    ): AlertsResponse = AlertsResponse(alerts = alertNotifier.findRecentAlerts(limit))

    @GetMapping("/slack-config")
    fun slackConfig(): SlackConfigSummary = slackConfigService.view()

    @PutMapping("/slack-config")
    fun updateSlackConfig(@Valid @RequestBody request: SlackConfigUpdateRequest): SlackConfigSummary =
        slackConfigService.update(request.toUpdate())

    @PostMapping("/slack-config/test")
    fun testSlackConfig(): SlackTestResult = slackConfigService.test()
}
