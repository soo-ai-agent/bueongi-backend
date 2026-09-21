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
                    kind =
                        clientKindOf(
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

    private fun callerOf(path: String): RequestCaller {
        for (prefix in ADMIN_PREFIXES) {
            if (path == prefix || path.startsWith("$prefix/")) {
                return RequestCaller.ADMIN
            }
        }
        return RequestCaller.USER
    }

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

    private fun storeFor(caller: RequestCaller): RequestMetricStore =
        when (caller) {
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
        const val CLIENT_PLATFORM_HEADER: String = "X-Client-Platform"
        val ADMIN_PREFIXES: List<String> = listOf("/api/admin", "/api/v1/auth")
        val EXCLUDED_PREFIXES: List<String> = listOf("/actuator", "/healthz", "/cdn")
        val METRIC_PREFIXES: List<String> = listOf("/api", "/share")
    }
}
