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
     * 질의 문자열은 이미 떼고 들어온다 — serviceKey·appKey 가 거기 실린다.
     * 위성 기호(🛰)로 시작해 수신 요청(🌐)과 한눈에 갈린다.
     */
    fun format(): String {
        val columns: MutableList<String> = mutableListOf(
            "🛰 $provider",
            statusText(),
            endpoint,
            "⏱ ${durationMs}ms",
        )
        if (failureReason != null) {
            columns.add("✖ $failureReason")
        }
        return columns.joinToString(SEPARATOR)
    }

    /** 연결 자체가 실패하면 응답 코드가 없다 — 숫자 0 대신 그렇게 적는다. */
    private fun statusText(): String {
        if (status <= 0) {
            return "무응답 $method"
        }
        return "$status $method"
    }

    fun needsAttention(): Boolean = status <= 0 || status >= CLIENT_ERROR_STATUS

    private companion object {
        const val SEPARATOR: String = "  |  "
        const val CLIENT_ERROR_STATUS: Int = 400
    }
}
