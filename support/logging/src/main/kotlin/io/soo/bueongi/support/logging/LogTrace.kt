package io.soo.bueongi.support.logging

import org.slf4j.MDC
import java.util.UUID

// 수신 필터가 요청마다 한 번 심고, 같은 스레드에서 나가는 송신·예외 줄이 주워 쓴다.
const val TRACE_ID_MDC_KEY: String = "bueongiTraceId"

const val REQUEST_ORIGIN_MDC_KEY: String = "bueongiRequestOrigin"

fun beginTrace(origin: String): String {
    // 로그에서 눈으로 짝을 맞추는 용도다 — 분산 추적 규격이 아니다.
    val traceId: String = UUID.randomUUID().toString().replace("-", "").take(TRACE_ID_LENGTH)
    MDC.put(TRACE_ID_MDC_KEY, traceId)
    MDC.put(REQUEST_ORIGIN_MDC_KEY, origin)
    return traceId
}

fun endTrace() {
    MDC.remove(TRACE_ID_MDC_KEY)
    MDC.remove(REQUEST_ORIGIN_MDC_KEY)
}

// MDC 는 값이 없으면 null 을 준다 — 요청 밖(스케줄러·비동기)에서 부르면 늘 그렇다.
fun currentTraceId(): String {
    return MDC.get(TRACE_ID_MDC_KEY) ?: ""
}

fun currentRequestOrigin(): String {
    return MDC.get(REQUEST_ORIGIN_MDC_KEY) ?: ""
}

fun traceColumn(): String {
    val traceId: String = currentTraceId()
    if (traceId.isEmpty()) {
        return ""
    }
    return "🔗 $traceId"
}

private const val TRACE_ID_LENGTH: Int = 8
