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
            finish(request, startedAt, response.statusCode.value(), cause = null)
            return response
        } catch (e: IOException) {
            // 연결 실패·타임아웃은 응답 코드가 없다. 기록만 남기고 예외는 그대로 올려보낸다.
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
                // 원인 사슬이라야 "왜 실패했는지"가 보인다 — 마스킹은 causeChainOf 가 한다.
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

    /**
     * 적재 계열은 몇 페이지째에서 멈췄는지가 진단의 첫 단서다. 다만 질의 문자열에는 인증키가
     * 실리므로 이름이 허용 목록에 있고 값이 숫자뿐인 항목만 통과시킨다 — 키 값은 숫자가 아니다.
     */
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

    /** 기록에는 종류만 남긴다 — 관리자 화면에 그대로 보이는 값이라 짧아야 한다. */
    private fun reasonOf(e: Throwable): String =
        e::class.simpleName ?: "IOException"

    private companion object {
        val PAGING_PARAMS: Set<String> = setOf("pageno", "pageindex", "page", "numofrows", "pageunit", "perpage")
    }
}
