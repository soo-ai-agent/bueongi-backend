package io.soo.bueongi.api.config

import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 들어온 요청을 한 줄로 남기고 메트릭에 기록한다.
 *
 * 컨트롤러 AOP 가 아니라 필터인 이유는, 컨트롤러에 닿기 전에 끝나는 요청(404·인증 거부·
 * 검증 실패)도 남겨야 하고 최종 응답 상태는 필터 자리에서만 알 수 있기 때문이다.
 *
 * **질의 문자열은 남기지 않는다** — 검색어·좌표가 실려 개인정보가 된다. 경로의 공유 토큰도
 * [normalizeRequestPath] 가 `{token}` 으로 가린다.
 */
class RequestMetricsFilter(
    private val requestMetricStore: RequestMetricStore,
) : OncePerRequestFilter() {

    private val log: Logger = LoggerFactory.getLogger(RequestMetricsFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt: Long = System.currentTimeMillis()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs: Long = System.currentTimeMillis() - startedAt
            val path: String = normalizeRequestPath(request.requestURI)
            logRequest(request.method, path, response.status, durationMs)
            recordSafely(request, path, response.status, startedAt, durationMs)
        }
    }

    /** 서버 오류는 눈에 띄어야 해 warn 으로 올린다 — 나머지는 info 한 줄. */
    private fun logRequest(method: String, path: String, status: Int, durationMs: Long) {
        if (status >= SERVER_ERROR_STATUS) {
            log.warn("{} {} {} ({}ms)", method, path, status, durationMs)
            return
        }
        log.info("{} {} {} ({}ms)", method, path, status, durationMs)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path: String = request.requestURI
        for (prefix in EXCLUDED_PREFIXES) {
            if (path == prefix || path.startsWith("$prefix/")) {
                return true
            }
        }
        return false
    }

    private fun recordSafely(
        request: HttpServletRequest,
        path: String,
        status: Int,
        startedAt: Long,
        durationMs: Long,
    ) {
        try {
            requestMetricStore.record(
                RequestMetric(
                    atEpochMs = startedAt,
                    method = request.method,
                    endpoint = path,
                    clientId = request.getHeader(CLIENT_ID_HEADER)?.takeIf { it.isNotBlank() },
                    status = status,
                    durationMs = durationMs,
                ),
            )
        } catch (e: Exception) {
            log.warn("수신 요청 메트릭 계측 실패(무시): {}", e.message)
        }
    }

    private companion object {
        const val CLIENT_ID_HEADER: String = "X-Client-Id"

        const val SERVER_ERROR_STATUS: Int = 500

        val EXCLUDED_PREFIXES: List<String> = listOf("/api/admin", "/actuator", "/healthz", "/cdn")
    }
}
