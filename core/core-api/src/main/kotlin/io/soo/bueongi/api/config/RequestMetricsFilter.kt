package io.soo.bueongi.api.config

import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 수신(incoming) 요청 트래픽 계측 필터. 체인을 그대로 실행한 뒤 메서드·정규화 경로·익명 클라이언트 식별자
 * (`X-Client-Id`)·응답 status·소요시간을 [RequestMetricStore] 에 기록한다 — 관리자 "트래픽" 화면의 실데이터 원천.
 *
 * 대상은 [FilterRegistrationBean][io.soo.bueongi.api.config.RequestMetricsFilterConfig] 의 url 패턴(`/api`·`/share` 하위)이고,
 * [shouldNotFilter] 로 관리자 자기 트래픽(`/api/admin` 하위)과 운영 경로(`/actuator`·`/healthz`·`/cdn`)를 뺀다
 * (관리자 트래픽을 사용자 집계에 넣지 않기 위함).
 *
 * 계측은 요청 처리의 순수 부수효과다 — 기록 중 어떤 오류가 나도 **요청을 절대 깨지 않는다**(try/catch 로 삼키고 경고만).
 * 저장소의 [RequestMetricStore.record] 도 예외를 던지지 않지만, 정규화·헤더 읽기·status 조회까지 방어적으로 감싼다.
 */
class RequestMetricsFilter(
    private val requestMetricStore: RequestMetricStore,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RequestMetricsFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = System.currentTimeMillis()
        try {
            filterChain.doFilter(request, response)
        } finally {
            recordSafely(request, response, startedAt)
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return EXCLUDED_PREFIXES.any { prefix -> path == prefix || path.startsWith("$prefix/") }
    }

    /** 계측 기록. 실패해도 요청 흐름을 깨지 않도록 전체를 감싼다(정규화/헤더/status 조회 포함). */
    private fun recordSafely(request: HttpServletRequest, response: HttpServletResponse, startedAt: Long) {
        try {
            requestMetricStore.record(
                RequestMetric(
                    atEpochMs = startedAt,
                    method = request.method,
                    endpoint = RequestPathNormalizer.normalize(request.requestURI),
                    clientId = request.getHeader(CLIENT_ID_HEADER)?.takeIf { it.isNotBlank() },
                    status = response.status,
                    durationMs = System.currentTimeMillis() - startedAt,
                ),
            )
        } catch (e: Exception) {
            log.warn("수신 요청 메트릭 계측 실패(무시): {}", e.message)
        }
    }

    private companion object {
        /** 프론트가 부여하는 익명 클라이언트 식별자 헤더 — 사용자 추정(고유 수)의 근거. */
        const val CLIENT_ID_HEADER = "X-Client-Id"

        /** 계측에서 제외하는 경로 접두사(정확 일치 또는 하위 경로). 관리자 자기 트래픽·운영 경로는 사용자 집계에서 뺀다. */
        val EXCLUDED_PREFIXES = listOf("/api/admin", "/actuator", "/healthz", "/cdn")
    }
}
