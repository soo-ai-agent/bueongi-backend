package io.soo.bueongi.api.config

/** 외부 API 호출 한 건을 로그 한 줄로 옮긴 값. 수신 요청 줄([RequestLogLine])과 칸 순서를 맞춘다. */
data class ExternalCallLogLine(
    val provider: String,
    val method: String,
    val endpoint: String,
    val status: Int,
    val durationMs: Long,
    // 성공했으면 실패 사유가 없어 null 이 가능함
    val failureReason: String?,
) {
    /**
     * 맨 앞이 방향이다 — `[송신]` 으로 시작해 우리가 받은 요청(`[수신]`)과 낱말로 갈린다.
     * 상대 자리에는 IP 대신 원천 이름이 온다. 질의 문자열은 이미 떼고 들어온다 —
     * serviceKey·appKey 가 거기 실린다.
     */
    fun format(): String {
        val columns: MutableList<String> = mutableListOf(
            padColumn(OUTBOUND_DIRECTION, DIRECTION_COLUMN_WIDTH),
            padColumn(provider, COUNTERPART_COLUMN_WIDTH),
            padColumn(statusText(), STATUS_COLUMN_WIDTH),
            endpoint,
            "${durationMs}ms".padStart(DURATION_COLUMN_WIDTH),
        )
        if (failureReason != null) {
            columns.add("✖ $failureReason")
        }
        return columns.joinToString(LOG_COLUMN_SEPARATOR)
    }

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
