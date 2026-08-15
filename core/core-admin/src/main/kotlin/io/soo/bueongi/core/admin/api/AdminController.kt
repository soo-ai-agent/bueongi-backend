package io.soo.bueongi.core.admin.api

import io.soo.bueongi.support.monitoring.AlertNotifier

import io.soo.bueongi.core.admin.domain.adstats.AdStatsService
import io.soo.bueongi.core.admin.domain.adstats.AdStatsSummary
import io.soo.bueongi.core.admin.api.response.AdminIdentityResponse
import io.soo.bueongi.core.admin.api.response.AlertsResponse
import io.soo.bueongi.core.admin.api.response.ApiCallLogResponse
import io.soo.bueongi.core.admin.domain.apistatus.ApiStatusService
import io.soo.bueongi.core.admin.domain.apistatus.ApiStatusSummary
import io.soo.bueongi.core.admin.domain.facility.FacilityStatusService
import io.soo.bueongi.core.admin.domain.facility.FacilityStatusSummary
import io.soo.bueongi.core.admin.domain.traffic.RequestMetricsService
import io.soo.bueongi.core.admin.domain.routestats.RouteStatsService
import io.soo.bueongi.core.admin.domain.routestats.RouteStatsSummary
import io.soo.bueongi.core.admin.api.response.ShareSessionsResponse
import io.soo.bueongi.core.admin.api.response.ShareStatusResponse
import io.soo.bueongi.core.admin.domain.slack.SlackConfigService
import io.soo.bueongi.core.admin.domain.slack.SlackConfigSummary
import io.soo.bueongi.core.admin.domain.slack.SlackTestResult
import io.soo.bueongi.core.admin.domain.traffic.TrafficSummary

import io.soo.bueongi.core.admin.api.request.SlackConfigUpdateRequest
import io.soo.bueongi.core.share.domain.LocationView
import io.soo.bueongi.core.share.domain.ShareService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val apiStatusService: ApiStatusService,
    private val facilityStatusService: FacilityStatusService,
    private val routeStatsService: RouteStatsService,
    private val requestMetricsService: RequestMetricsService,
    private val adStatsService: AdStatsService,
    private val shareService: ShareService,
    private val alertNotifier: AlertNotifier,
    private val slackConfigService: SlackConfigService,
) {

    @GetMapping("/me")
    fun me(authentication: Authentication): AdminIdentityResponse = AdminIdentityResponse.from(authentication)

    @GetMapping("/api-logs")
    fun apiLogs(
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
    ): List<ApiCallLogResponse> =
        apiStatusService.listRecentCalls(provider, limit).map { ApiCallLogResponse.from(it) }

    @GetMapping("/api-status")
    fun apiStatus(): ApiStatusSummary = apiStatusService.summarize()

    @GetMapping("/facility-status")
    fun facilityStatus(): FacilityStatusSummary = facilityStatusService.summarize()

    @GetMapping("/route-stats")
    fun routeStats(): RouteStatsSummary = routeStatsService.summarize()

    @GetMapping("/traffic")
    fun traffic(): TrafficSummary = requestMetricsService.summarize()

    @GetMapping("/ad-stats")
    fun adStats(): AdStatsSummary = adStatsService.summarize()

    @GetMapping("/share-status")
    fun shareStatus(): ShareStatusResponse = ShareStatusResponse(activeSessions = shareService.countActiveSessions())

    @GetMapping("/share-sessions")
    fun shareSessions(): ShareSessionsResponse = ShareSessionsResponse(sessions = shareService.listActiveSessions())

    @GetMapping("/share-sessions/{token}/location")
    fun shareSessionLocation(@PathVariable token: String): LocationView =
        shareService.getLocationForAdmin(token)

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

