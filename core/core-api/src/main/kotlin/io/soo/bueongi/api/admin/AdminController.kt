package io.soo.bueongi.api.admin

import io.soo.bueongi.domain.admin.ApiStatusService
import io.soo.bueongi.domain.admin.ApiStatusSummary
import io.soo.bueongi.domain.admin.FacilityStatusService
import io.soo.bueongi.domain.admin.FacilityStatusSummary
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_DEFAULT_LIMIT
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_MAX_LIMIT
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 페이지용 API. `/api/admin` 하위 경로는 SecurityConfig 가 `ROLE_ADMIN` 으로 보호한다(엔드포인트별 게이트 불필요).
 * [me] 는 인증 배선 검증용 최소 엔드포인트, [apiLogs] 는 외부 API 접속 내역 조회다.
 * 컨트롤러는 변환만 한다 — 저장소 조회 결과를 응답 DTO 로 매핑하고 조회 상한만 경계 검증한다.
 */
@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val apiCallLogStore: ApiCallLogStore,
    private val apiStatusService: ApiStatusService,
    private val facilityStatusService: FacilityStatusService,
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
        val bounded = limit.coerceIn(1, API_CALL_LOG_MAX_LIMIT)
        return apiCallLogStore.findRecent(provider, bounded).map { it.toResponse() }
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
}

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
