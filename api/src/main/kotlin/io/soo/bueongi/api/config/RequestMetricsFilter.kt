package io.soo.bueongi.api.config

import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청을 한 줄로 남기고 메트릭에 기록한다.
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
            val clientId: String? = request.getHeader(CLIENT_ID_HEADER)?.takeIf { it.isNotBlank() }
            val caller: RequestCaller = callerOf(path)
            logRequest(
                RequestLogLine(
                    method = request.method,
                    path = path,
                    status = response.status,
                    durationMs = durationMs,
                    clientId = clientId,
                    address = clientAddressOf(request),
                    kind = clientKindOf(
                        declaredPlatform = request.getHeader(CLIENT_PLATFORM_HEADER),
                        userAgent = request.getHeader(HttpHeaders.USER_AGENT),
                    ),
                    caller = caller,
                ),
            )
            if (caller == RequestCaller.USER && isOurApi(path)) {
                recordSafely(clientId, path, request.method, response.status, startedAt, durationMs)
            }
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

    /**
     * 관리자 화면의 조회는 사용자 트래픽이 아니다. 로그에는 남기되(누가 언제 봤는지가 필요하다)
     * 트래픽 지표에는 넣지 않는다 — 미리보기 5초 폴링이 사용자 수·호출량을 부풀린다.
     */
    private fun callerOf(path: String): RequestCaller {
        if (path == ADMIN_PREFIX || path.startsWith("$ADMIN_PREFIX/")) {
            return RequestCaller.ADMIN
        }
        return RequestCaller.USER
    }

    /** 트래픽 통계는 우리 API 만 센다 — 스캐너가 두드리는 임의 경로까지 세면 지표가 망가진다. */
    private fun isOurApi(path: String): Boolean {
        for (prefix in METRIC_PREFIXES) {
            if (path == prefix || path.startsWith("$prefix/")) {
                return true
            }
        }
        return false
    }

    private fun logRequest(line: RequestLogLine) {
        if (line.needsAttention()) {
            log.warn(line.format())
            return
        }
        log.info(line.format())
    }

    private fun recordSafely(
        clientId: String?,
        path: String,
        method: String,
        status: Int,
        startedAt: Long,
        durationMs: Long,
    ) {
        try {
            requestMetricStore.record(
                RequestMetric(
                    atEpochMs = startedAt,
                    method = method,
                    endpoint = path,
                    clientId = clientId,
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

        /** 앱이 자기 정체를 직접 밝히는 헤더 — UA 짐작보다 정확하다. */
        const val CLIENT_PLATFORM_HEADER: String = "X-Client-Platform"
        const val ADMIN_PREFIX: String = "/api/admin"

        // /api/admin 은 더 이상 빼지 않는다 — 관리자가 무엇을 조회했는지 로그에 보여야 한다.
        val EXCLUDED_PREFIXES: List<String> = listOf("/actuator", "/healthz", "/cdn")
        val METRIC_PREFIXES: List<String> = listOf("/api", "/share")
    }
}
