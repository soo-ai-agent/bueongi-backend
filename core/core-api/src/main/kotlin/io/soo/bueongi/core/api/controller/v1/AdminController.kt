package io.soo.bueongi.core.api.controller.v1

import io.soo.bueongi.support.monitoring.ALERT_HISTORY_MAX_LIMIT
import io.soo.bueongi.support.monitoring.AlertNotifier

import io.soo.bueongi.core.domain.admin.AdStatsService
import io.soo.bueongi.core.domain.admin.AdStatsSummary
import io.soo.bueongi.core.domain.admin.AdminIdentity
import io.soo.bueongi.core.api.controller.v1.response.AlertsResponse
import io.soo.bueongi.core.api.controller.v1.response.ApiCallLogResponse
import io.soo.bueongi.core.domain.admin.ApiStatusService
import io.soo.bueongi.core.domain.admin.ApiStatusSummary
import io.soo.bueongi.core.domain.admin.FacilityStatusService
import io.soo.bueongi.core.domain.admin.FacilityStatusSummary
import io.soo.bueongi.core.domain.admin.RequestMetricsService
import io.soo.bueongi.core.domain.admin.RouteStatsService
import io.soo.bueongi.core.domain.admin.RouteStatsSummary
import io.soo.bueongi.core.api.controller.v1.response.ShareSessionsResponse
import io.soo.bueongi.core.api.controller.v1.response.ShareStatusResponse
import io.soo.bueongi.core.domain.admin.SlackConfigService
import io.soo.bueongi.core.domain.admin.SlackConfigSummary
import io.soo.bueongi.core.domain.admin.SlackTestResult
import io.soo.bueongi.core.domain.admin.TrafficSummary

import io.soo.bueongi.core.api.controller.v1.request.SlackConfigUpdateRequest
import io.soo.bueongi.core.domain.share.ActiveShareView
import io.soo.bueongi.core.domain.share.LocationView
import io.soo.bueongi.core.domain.share.ShareService
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_DEFAULT_LIMIT
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_MAX_LIMIT
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
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
    fun me(authentication: Authentication): AdminIdentity =
        AdminIdentity(
            userId = authentication.principal as Long,
            roles = authentication.authorities.map { it.authority },
        )

    @GetMapping("/api-logs")
    fun apiLogs(
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
    ): List<ApiCallLogResponse> {
        val bounded: Int = limit.coerceIn(1, API_CALL_LOG_MAX_LIMIT)
        return apiStatusService.listRecentCalls(provider, bounded).map { it.toResponse() }
    }

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
    ): AlertsResponse {
        val bounded: Int = limit.coerceIn(1, ALERT_HISTORY_MAX_LIMIT)
        return AlertsResponse(alerts = alertNotifier.findRecentAlerts(bounded))
    }

    @GetMapping("/slack-config")
    fun slackConfig(): SlackConfigSummary = slackConfigService.view()

    @PutMapping("/slack-config")
    fun updateSlackConfig(@Valid @RequestBody request: SlackConfigUpdateRequest): SlackConfigSummary =
        slackConfigService.update(request.toUpdate())

    @PostMapping("/slack-config/test")
    fun testSlackConfig(): SlackTestResult = slackConfigService.test()
}






private fun ApiCallLog.toResponse(): ApiCallLogResponse =
    ApiCallLogResponse(
        atEpochMs = atEpochMs,
        provider = provider,
        endpoint = endpoint,
        success = success,
        durationMs = durationMs,
        detail = detail,
    )
