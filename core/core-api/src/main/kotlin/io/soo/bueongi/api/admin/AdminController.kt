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

/**
 * 관리자 페이지용 API. `/api/admin` 하위 경로는 SecurityConfig 가 `ROLE_ADMIN` 으로 보호한다(엔드포인트별 게이트 불필요).
 * [me] 는 인증 배선 검증용 최소 엔드포인트, [apiLogs] 는 외부 API 접속 내역 조회다.
 * 컨트롤러는 변환만 한다 — 조회는 전부 도메인 서비스에 위임하고(저장소 직접 주입 없음),
 * 도메인 결과를 응답 DTO 로 매핑하고 조회 상한만 경계 검증한다.
 */
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

    /** GET /api/admin/me → 현재 관리자 principal(userId)과 권한 목록. */
    @GetMapping("/me")
    fun me(authentication: Authentication): AdminIdentity =
        AdminIdentity(
            userId = authentication.principal as Long,
            roles = authentication.authorities.map { it.authority },
        )

    /**
     * GET /api/admin/api-logs?provider=&limit= → 외부 API 접속 내역(최근순).
     * provider 미지정이면 전체. limit 는 [1, [API_CALL_LOG_MAX_LIMIT]] 로 절삭(기본 [API_CALL_LOG_DEFAULT_LIMIT]).
     */
    @GetMapping("/api-logs")
    fun apiLogs(
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
    ): List<ApiCallLogResponse> {
        val bounded: Int = limit.coerceIn(1, API_CALL_LOG_MAX_LIMIT)
        return apiStatusService.listRecentCalls(provider, bounded).map { it.toResponse() }
    }

    /**
     * GET /api/admin/api-status → 외부 API 원천별 상태 요약·쿼터([ApiStatusSummary]).
     * 저장된 접속 내역에서 집계만 한다(원천 4종 고정 노출).
     */
    @GetMapping("/api-status")
    fun apiStatus(): ApiStatusSummary = apiStatusService.summarize()

    /**
     * GET /api/admin/facility-status → 안심시설 종류별 데이터 현황([FacilityStatusSummary]).
     * 적재 건수·마지막 라이브 갱신 결과·CDN 신선도를 집계해 돌려준다(변환만).
     */
    @GetMapping("/facility-status")
    fun facilityStatus(): FacilityStatusSummary = facilityStatusService.summarize()

    /**
     * GET /api/admin/route-stats → 경로 추천 요청 집계([RouteStatsSummary]).
     * 저장된 추천 기록에서 엔드포인트별 수·안심강도 선호 분포·상위 목적지·최근 24시간 트렌드를 집계만 한다(변환만).
     */
    @GetMapping("/route-stats")
    fun routeStats(): RouteStatsSummary = routeStatsService.summarize()

    /**
     * GET /api/admin/traffic → 수신 요청 트래픽 집계([TrafficSummary]).
     * 저장된 수신 요청 기록에서 엔드포인트별 호출수·예측 사용자 수(고유 익명 client-id)·시간대/일자 추이를 집계만 한다(변환만).
     */
    @GetMapping("/traffic")
    fun traffic(): TrafficSummary = requestMetricsService.summarize()

    /**
     * GET /api/admin/ad-stats → 광고 노출(호출) 집계([AdStatsSummary]).
     * 저장된 광고 노출 기록에서 총계·오늘 노출·지면별 카운트·최근 24시간 트렌드를 집계만 한다(변환만).
     */
    @GetMapping("/ad-stats")
    fun adStats(): AdStatsSummary = adStatsService.summarize()

    /** GET /api/admin/share-status → 현재 활성(상한 미만료) 위치공유 세션 수(집계는 [ShareService] 가 수행 — 변환만). */
    @GetMapping("/share-status")
    fun shareStatus(): ShareStatusResponse = ShareStatusResponse(activeSessions = shareService.countActiveSessions())

    /**
     * GET /api/admin/share-sessions → 현재 이동 중인(활성) 위치공유 세션 목록.
     * 세션별 공유 URL·현재 위치·TTL·시청 여부를 담는다(변환만 — URL 조립·TTL 파생·정렬은 [ShareService] 가 수행).
     * owner_secret·경로 상세는 도메인에서 이미 배제된다(프라이버시 불변식).
     */
    @GetMapping("/share-sessions")
    fun shareSessions(): ShareSessionsResponse = ShareSessionsResponse(sessions = shareService.listActiveSessions())

    /**
     * GET /api/admin/share-sessions/{token}/location → 특정 공유 세션의 현재 위치(관리자 앱 내 열람).
     * 공개 `GET /share/{token}/location` 과 동일한 [LocationView] 계약(snake_case)을 주되, 관리자 열람을
     * 보호자 시청(viewerActive)으로 집계하지 않는다(markViewed 미호출 — [ShareService.getLocationForAdmin]).
     * 토큰 없음/만료는 공개 조회와 동일 규약(`expired:true`, 좌표 null). 변환만 — 상태 판정은 도메인이 한다.
     */
    @GetMapping("/share-sessions/{token}/location")
    fun shareSessionLocation(@PathVariable token: String): LocationView =
        shareService.getLocationForAdmin(token)

    /**
     * GET /api/admin/alerts?limit= → 최근 장애 알림 이력(최신순).
     * limit 는 [1, [ALERT_HISTORY_MAX_LIMIT]] 로 절삭(기본 100).
     */
    @GetMapping("/alerts")
    fun alerts(
        @RequestParam(required = false, defaultValue = "100") limit: Int,
    ): AlertsResponse {
        val bounded: Int = limit.coerceIn(1, ALERT_HISTORY_MAX_LIMIT)
        return AlertsResponse(alerts = alertNotifier.findRecentAlerts(bounded))
    }

    /**
     * GET /api/admin/slack-config → 현재 Slack 알림 설정([SlackConfigSummary]).
     * webhook 원문은 노출하지 않는다(마스킹만). 변환만 — 집계·마스킹은 서비스가 한다.
     */
    @GetMapping("/slack-config")
    fun slackConfig(): SlackConfigSummary = slackConfigService.view()

    /**
     * PUT /api/admin/slack-config → 발송 토글·스로틀·카테고리 뮤트를 편집하고 갱신된 요약을 반환.
     * webhook 은 편집 불가(요청 DTO 에 없음). 요청은 @Valid 로 검증하고 도메인 입력으로 변환만 한다.
     */
    @PutMapping("/slack-config")
    fun updateSlackConfig(@Valid @RequestBody request: SlackConfigUpdateRequest): SlackConfigSummary =
        slackConfigService.update(request.toUpdate())

    /** POST /api/admin/slack-config/test → 테스트 발송을 시도하고 결과([SlackTestResult])를 반환(변환만). */
    @PostMapping("/slack-config/test")
    fun testSlackConfig(): SlackTestResult = slackConfigService.test()
}

/** share-status 응답 — 현재 활성 공유 세션 수. */
data class ShareStatusResponse(
    val activeSessions: Int,
)

/** share-sessions 응답 — 현재 이동 중인(활성) 위치공유 세션 목록(관리자). */
data class ShareSessionsResponse(
    val sessions: List<ActiveShareView>,
)

/** alerts 응답 — 최근 장애 알림 이력(최신순). */
data class AlertsResponse(
    val alerts: List<AlertRecord>,
)

data class AdminIdentity(
    val userId: Long,
    val roles: List<String>,
)

/** 외부 API 접속 내역 응답 1건 — 저장 모델([ApiCallLog])의 웹 표현(레이어 분리). */
data class ApiCallLogResponse(
    val atEpochMs: Long,
    val provider: String,
    val endpoint: String,
    val success: Boolean,
    val durationMs: Long,
    val detail: String?,
)

private fun ApiCallLog.toResponse(): ApiCallLogResponse =
    ApiCallLogResponse(
        atEpochMs = atEpochMs,
        provider = provider,
        endpoint = endpoint,
        success = success,
        durationMs = durationMs,
        detail = detail,
    )
