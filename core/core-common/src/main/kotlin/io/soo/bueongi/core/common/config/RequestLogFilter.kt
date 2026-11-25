package io.soo.bueongi.core.common.config

import io.soo.bueongi.support.logging.beginTrace
import io.soo.bueongi.support.logging.endTrace
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter

class RequestLogFilter : OncePerRequestFilter() {
    private val log: Logger = LoggerFactory.getLogger(RequestLogFilter::class.java)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val startedAt: Long = System.currentTimeMillis()
        val path: String = normalizeRequestPath(request.requestURI)
        beginTrace("${request.method} $path")
        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs: Long = System.currentTimeMillis() - startedAt
            val clientIdHeader: String? = request.getHeader(CLIENT_ID_HEADER)
            var clientId: String? = null
            if (!clientIdHeader.isNullOrBlank()) {
                clientId = clientIdHeader
            }
            val caller: RequestCaller = callerOf(path)
            logRequest(
                RequestLogLine(
                    method = request.method,
                    path = path,
                    status = response.status,
                    durationMs = durationMs,
                    clientId = clientId,
                    caller = caller,
                ),
            )
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

    private fun logRequest(line: RequestLogLine) {
        if (line.isError()) {
            log.warn(line.format())
            return
        }
        log.info(line.format())
    }

    private companion object {
        const val CLIENT_ID_HEADER: String = "X-Client-Id"
        val ADMIN_PREFIXES: List<String> = listOf("/api/admin", "/api/v1/auth")
        val EXCLUDED_PREFIXES: List<String> = listOf("/healthz", "/cdn")
    }
}
