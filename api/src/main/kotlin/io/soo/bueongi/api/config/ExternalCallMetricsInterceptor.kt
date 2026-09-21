package io.soo.bueongi.api.config

import io.soo.bueongi.core.enums.ExternalApiProvider
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetric
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetricStore
import io.soo.bueongi.support.logging.ExceptionLogLine
import io.soo.bueongi.support.logging.IGNORED_EXCEPTION
import io.soo.bueongi.support.logging.causeChainOf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.IOException

class ExternalCallMetricsInterceptor(
    private val externalCallMetricStore: ExternalCallMetricStore,
    private val clock: java.time.Clock,
) : ClientHttpRequestInterceptor {

    private val log: Logger = LoggerFactory.getLogger(ExternalCallMetricsInterceptor::class.java)

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        val startedAt: Long = clock.millis()
        try {
            val response: ClientHttpResponse = execution.execute(request, body)
            finish(request, startedAt, response.statusCode.value(), cause = null)
            return response
        } catch (e: IOException) {
            finish(request, startedAt, status = 0, cause = e)
            throw e
        }
    }

    private fun finish(request: HttpRequest, startedAt: Long, status: Int, cause: Throwable?) {
        val durationMs: Long = clock.millis() - startedAt
        val provider: ExternalApiProvider = providerOfHost(request.uri.host)
        val endpoint: String = request.uri.path
        val method: String = request.method.name()
        logCall(
            ExternalCallLogLine(
                provider = provider.wireValue,
                method = method,
                endpoint = endpoint,
                status = status,
                durationMs = durationMs,
                failureReason = if (cause == null) "" else causeChainOf(cause),
                paging = pagingOf(request.uri.rawQuery),
            ),
        )
        recordSafely(
            ExternalCallMetric(
                atEpochMs = startedAt,
                provider = provider,
                method = method,
                endpoint = endpoint,
                status = status,
                durationMs = durationMs,
                failureReason = cause?.let { reasonOf(it) },
            ),
        )
    }

    private fun logCall(line: ExternalCallLogLine) {
        if (line.needsAttention()) {
            log.warn(line.format())
            return
        }
        log.info(line.format())
    }

    private fun recordSafely(metric: ExternalCallMetric) {
        try {
            externalCallMetricStore.record(metric)
        } catch (e: Exception) {
            log.warn(
                ExceptionLogLine(
                    kind = IGNORED_EXCEPTION,
                    counterpart = "호출 계측",
                    subject = metric.endpoint,
                    cause = causeChainOf(e),
                    action = "외부 호출 결과는 그대로 사용(계측만 유실)",
                ).format(),
            )
        }
    }

    private fun pagingOf(rawQuery: String?): String {
        if (rawQuery.isNullOrEmpty()) {
            return ""
        }
        val shown: MutableList<String> = mutableListOf()
        for (pair in rawQuery.split("&")) {
            val name: String = pair.substringBefore("=")
            val value: String = pair.substringAfter("=", "")
            val isPaging: Boolean = name.lowercase() in PAGING_PARAMS
            val isNumber: Boolean = value.isNotEmpty() && value.all { it.isDigit() }
            if (isPaging && isNumber) {
                shown.add("$name=$value")
            }
        }
        return shown.joinToString(" ")
    }

    private fun reasonOf(e: Throwable): String =
        e::class.simpleName ?: "IOException"

    private companion object {
        val PAGING_PARAMS: Set<String> = setOf("pageno", "pageindex", "page", "numofrows", "pageunit", "perpage")
    }
}
