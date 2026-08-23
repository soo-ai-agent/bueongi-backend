package io.soo.bueongi.api.config

import io.soo.bueongi.support.logging.BACKGROUND_ORIGIN
import io.soo.bueongi.support.logging.COUNTERPART_COLUMN_WIDTH
import io.soo.bueongi.support.logging.DIRECTION_COLUMN_WIDTH
import io.soo.bueongi.support.logging.DURATION_COLUMN_WIDTH
import io.soo.bueongi.support.logging.LOG_COLUMN_SEPARATOR
import io.soo.bueongi.support.logging.NO_RESPONSE_STATUS
import io.soo.bueongi.support.logging.OUTBOUND_DIRECTION
import io.soo.bueongi.support.logging.STATUS_COLUMN_WIDTH
import io.soo.bueongi.support.logging.currentRequestOrigin
import io.soo.bueongi.support.logging.padColumn
import io.soo.bueongi.support.logging.traceColumn

/** 외부 API 호출 한 건을 로그 한 줄로 옮긴 값. 수신 요청 줄([RequestLogLine])과 칸 순서를 맞춘다. */
data class ExternalCallLogLine(
    val provider: String,
    val method: String,
    val endpoint: String,
    val status: Int,
    val durationMs: Long,
    val failureReason: String = "",
    val paging: String = "",
) {
    /**
     * 맨 앞이 방향이다 — `[송신]` 으로 시작해 우리가 받은 요청(`[수신]`)과 낱말로 갈린다.
     * 상대 자리에는 IP 대신 원천 이름이 온다. 질의 문자열은 통째로 떼고 들어온다 —
     * serviceKey·appKey 가 거기 실린다. 뒤따르는 표식 칸이 "왜 불렀는지"를 말한다:
     * 🔗 어느 수신 요청에서 갈라졌는지, ↩ 그 요청이 무엇이었는지(없으면 `배치`),
     * 📄 몇 페이지째인지(질의 문자열 중 숫자 값 페이징 항목만), ✖ 무엇이 잘못됐는지.
     */
    fun format(): String {
        val columns: MutableList<String> = mutableListOf(
            padColumn(OUTBOUND_DIRECTION, DIRECTION_COLUMN_WIDTH),
            padColumn(provider, COUNTERPART_COLUMN_WIDTH),
            padColumn(statusText(), STATUS_COLUMN_WIDTH),
            endpoint,
            "${durationMs}ms".padStart(DURATION_COLUMN_WIDTH),
        )
        val trace: String = traceColumn()
        if (trace.isNotEmpty()) {
            columns.add(trace)
        }
        columns.add("↩ $origin")
        if (paging.isNotEmpty()) {
            columns.add("📄 $paging")
        }
        if (failureReason.isNotEmpty()) {
            columns.add("✖ $failureReason")
        }
        return columns.joinToString(LOG_COLUMN_SEPARATOR)
    }

    /**
     * 수신 요청이 낳은 호출인지, 스케줄러·비동기 적재가 스스로 낸 호출인지 —
     * 같은 원천의 호출이 뒤섞여 어느 기능이 쿼터를 태우는지 못 짚던 것을 여기서 가른다.
     */
    private val origin: String
        get() = currentRequestOrigin().ifEmpty { BACKGROUND_ORIGIN }

    /** 연결 자체가 실패하면 응답 코드가 없다 — 숫자 0 대신 코드 자리를 비웠음을 그대로 적는다. */
    private fun statusText(): String {
        if (status <= 0) {
            return "$NO_RESPONSE_STATUS $method"
        }
        return "$status $method"
    }

    fun needsAttention(): Boolean = status <= 0 || status >= CLIENT_ERROR_STATUS

    private companion object {
        const val CLIENT_ERROR_STATUS: Int = 400
    }
}
