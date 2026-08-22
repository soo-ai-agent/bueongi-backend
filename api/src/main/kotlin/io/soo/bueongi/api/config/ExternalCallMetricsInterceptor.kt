package io.soo.bueongi.api.config

import io.soo.bueongi.core.enums.ExternalApiProvider
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetric
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetricStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.IOException

/**
 * 외부 API 호출을 한 줄로 남기고 기록한다.
 *
 * 클라이언트마다 거는 대신 공용 `RestClient.Builder` 에 한 번 건다 — 모든 clients 모듈이
 * 그 빌더를 주입받으므로 새 클라이언트도 자동으로 걸린다([ExternalCallMetricsConfig]).
 */
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
            finish(request, startedAt, response.statusCode.value(), failureReason = null)
            return response
        } catch (e: IOException) {
            // 연결 실패·타임아웃은 응답 코드가 없다. 기록만 남기고 예외는 그대로 올려보낸다.
            finish(request, startedAt, status = 0, failureReason = reasonOf(e))
            throw e
        }
    }

    private fun finish(request: HttpRequest, startedAt: Long, status: Int, failureReason: String?) {
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
                failureReason = failureReason,
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
                failureReason = failureReason,
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
            log.warn("외부 API 호출 계측 실패(무시): {}", e.message)
        }
    }

    /** 예외 메시지에 인증키가 실린 URL 이 들어올 수 있어 예외 종류만 남긴다. */
    private fun reasonOf(e: IOException): String =
        e::class.simpleName ?: "IOException"
}
