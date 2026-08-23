package io.soo.bueongi.support.logging

import org.slf4j.MDC
import java.util.UUID

/**
 * 한 요청에서 갈라져 나간 줄들을 잇는 표식.
 *
 * 경로 요청 한 건이 Tmap 을 일곱 번 부른다 — 표식이 없으면 그 일곱 줄이 한 요청인지
 * 일곱 요청인지 로그만 보고는 못 가른다. 수신 필터가 요청마다 한 번 심고, 같은 스레드에서
 * 나가는 송신·예외 줄이 그대로 주워 쓴다.
 */
const val TRACE_ID_MDC_KEY: String = "bueongiTraceId"

/** 그 요청이 무엇이었는지(`POST /api/routes`) — 송신 줄이 "왜 불렸는지"로 적는다. */
const val REQUEST_ORIGIN_MDC_KEY: String = "bueongiRequestOrigin"

/** 수신 요청 없이 나가는 호출(스케줄러·비동기 적재)은 이 낱말로 갈린다. */
const val BACKGROUND_ORIGIN: String = "배치"

/** 로그에서 눈으로 짝을 맞추는 용도라 8자면 충분하다 — 분산 추적 규격이 아니다. */
fun newTraceId(): String = UUID.randomUUID().toString().replace("-", "").take(TRACE_ID_LENGTH)

fun beginTrace(origin: String): String {
    val traceId: String = newTraceId()
    MDC.put(TRACE_ID_MDC_KEY, traceId)
    MDC.put(REQUEST_ORIGIN_MDC_KEY, origin)
    return traceId
}

fun endTrace() {
    MDC.remove(TRACE_ID_MDC_KEY)
    MDC.remove(REQUEST_ORIGIN_MDC_KEY)
}

// MDC 는 값이 없으면 null 을 준다 — 요청 밖(스케줄러·비동기)에서 부르면 늘 그렇다.
fun currentTraceId(): String = MDC.get(TRACE_ID_MDC_KEY) ?: ""

fun currentRequestOrigin(): String = MDC.get(REQUEST_ORIGIN_MDC_KEY) ?: ""

/** 표식이 없는 줄(요청 밖)에서는 빈 칸을 만들지 않고 아예 뺀다. */
fun traceColumn(): String {
    val traceId: String = currentTraceId()
    if (traceId.isEmpty()) {
        return ""
    }
    return "🔗 $traceId"
}

private const val TRACE_ID_LENGTH: Int = 8
