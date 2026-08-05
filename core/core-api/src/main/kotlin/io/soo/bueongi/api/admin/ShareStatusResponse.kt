package io.soo.bueongi.api.admin

import io.soo.bueongi.api.admin.request.SlackConfigUpdateRequest
import io.soo.bueongi.domain.admin.AdStatsService
import io.soo.bueongi.domain.admin.AdStatsSummary
import io.soo.bueongi.domain.admin.ApiStatusService
import io.soo.bueongi.domain.admin.ApiStatusSummary
import io.soo.bueongi.domain.admin.FacilityStatusService
import io.soo.bueongi.domain.admin.FacilityStatusSummary
import io.soo.bueongi.domain.admin.RequestMetricsService
import io.soo.bueongi.domain.admin.RouteStatsService
import io.soo.bueongi.domain.admin.RouteStatsSummary
import io.soo.bueongi.domain.admin.TrafficSummary
import io.soo.bueongi.domain.admin.SlackConfigService
import io.soo.bueongi.domain.admin.SlackConfigSummary
import io.soo.bueongi.domain.admin.SlackTestResult
import io.soo.bueongi.domain.share.ActiveShareView
import io.soo.bueongi.domain.share.LocationView
import io.soo.bueongi.domain.share.ShareService
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_DEFAULT_LIMIT
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_MAX_LIMIT
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.support.monitoring.ALERT_HISTORY_MAX_LIMIT
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.AlertRecord
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

/** share-status 응답 — 현재 활성 공유 세션 수. */
data class ShareStatusResponse(
    val activeSessions: Int,
)
