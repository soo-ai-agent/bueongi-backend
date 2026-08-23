package io.soo.bueongi.api.config

import io.soo.bueongi.core.enums.RequestCaller
import io.soo.bueongi.storage.db.core.requestmetric.AdminRequestMetricStore
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import io.soo.bueongi.support.logging.ExceptionLogLine
import io.soo.bueongi.support.logging.IGNORED_EXCEPTION
import io.soo.bueongi.support.logging.beginTrace
import io.soo.bueongi.support.logging.causeChainOf
import io.soo.bueongi.support.logging.endTrace
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
    private val adminRequestMetricStore: AdminRequestMetricStore,
) : OncePerRequestFilter() {

    private val log: Logger = LoggerFactory.getLogger(RequestMetricsFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt: Long = System.currentTimeMillis()
        val path: String = normalizeRequestPath(request.requestURI)
        // 이 요청이 낳는 송신·예외 줄이 같은 표식을 달고 나가야 나중에 짝을 맞출 수 있다.
        beginTrace("${request.method} $path")
        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs: Long = System.currentTimeMillis() - startedAt
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
            if (isOurApi(path)) {
                recordSafely(caller, clientId, path, request.method, response.status, startedAt, durationMs)
            }
            endTrace()
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
     * 관리자 화면의 조회는 사용자 트래픽이 아니다. 버리지 않고 **다른 창에 따로** 담아
     * 화면에서 갈라 보여 준다 — 섞으면 미리보기 5초 폴링이 사용자 수·호출량을 부풀린다.
     */
    private fun callerOf(path: String): RequestCaller {
        for (prefix in ADMIN_PREFIXES) {
            if (path == prefix || path.startsWith("$prefix/")) {
                return RequestCaller.ADMIN
            }
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

    /** 부른 쪽마다 다른 창에 담는다 — 섞으면 한쪽이 다른 쪽을 밀어낸다. */
    private fun storeFor(caller: RequestCaller): RequestMetricStore = when (caller) {
        RequestCaller.ADMIN -> adminRequestMetricStore
        RequestCaller.USER -> requestMetricStore
    }

    private fun recordSafely(
        caller: RequestCaller,
        clientId: String?,
        path: String,
        method: String,
        status: Int,
        startedAt: Long,
        durationMs: Long,
    ) {
        try {
            storeFor(caller).record(
                RequestMetric(
                    atEpochMs = startedAt,
                    method = method,
                    endpoint = path,
                    clientId = clientId,
                    status = status,
                    durationMs = durationMs,
                    caller = caller,
                ),
            )
        } catch (e: Exception) {
            log.warn(
                ExceptionLogLine(
                    kind = IGNORED_EXCEPTION,
                    counterpart = "요청 계측",
                    subject = path,
                    cause = causeChainOf(e),
                    result = "$status $method",
                    action = "응답은 그대로 나감(통계만 유실)",
                ).format(),
            )
        }
    }

    private companion object {
        const val CLIENT_ID_HEADER: String = "X-Client-Id"

        /** 앱이 자기 정체를 직접 밝히는 헤더 — UA 짐작보다 정확하다. */
        const val CLIENT_PLATFORM_HEADER: String = "X-Client-Platform"
        /**
         * 관리자만 부르는 경로. `/api/v1/auth` 는 전부 AdminAuthController 라 사용자 호출자가 없고,
         * 토큰 갱신(수명 15분)이 관리자가 화면을 보는 동안 계속 나가 사용자 트래픽을 부풀렸다.
         */
        val ADMIN_PREFIXES: List<String> = listOf("/api/admin", "/api/v1/auth")

        // /api/admin 은 더 이상 빼지 않는다 — 관리자가 무엇을 조회했는지 로그에 보여야 한다.
        val EXCLUDED_PREFIXES: List<String> = listOf("/actuator", "/healthz", "/cdn")
        val METRIC_PREFIXES: List<String> = listOf("/api", "/share")
    }
}
