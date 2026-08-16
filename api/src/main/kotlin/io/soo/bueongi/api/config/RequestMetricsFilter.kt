package io.soo.bueongi.api.config

import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter

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
            recordSafely(request, response, startedAt)
        }
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

    private fun recordSafely(request: HttpServletRequest, response: HttpServletResponse, startedAt: Long) {
        try {
            requestMetricStore.record(
                RequestMetric(
                    atEpochMs = startedAt,
                    method = request.method,
                    endpoint = normalizeRequestPath(request.requestURI),
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
        const val CLIENT_ID_HEADER: String = "X-Client-Id"

        val EXCLUDED_PREFIXES: List<String> = listOf("/api/admin", "/actuator", "/healthz", "/cdn")
    }
}
