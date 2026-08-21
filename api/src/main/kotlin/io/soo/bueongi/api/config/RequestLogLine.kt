package io.soo.bueongi.api.config

/** 요청 한 건을 로그 한 줄로 옮긴 값. */
data class RequestLogLine(
    val method: String,
    val path: String,
    val status: Int,
    val durationMs: Long,
    // 헤더를 안 보내는 호출자(옛 앱·직접 호출)가 있어 null 이 가능함
    val clientId: String?,
    val address: String,
    val kind: String,
) {
    /**
     * IP 를 맨 앞에 둔다 — 침해 시도를 훑을 때 먼저 찾는 값이다.
     * 칸을 `|` 로 나눠 눈이 같은 자리를 따라가게 한다. 응답 본문·크기는 남기지 않는다.
     */
    fun format(): String = listOf(
        "🌐 $address",
        "$status $method",
        shortPath(),
        "⏱ ${durationMs}ms",
        "👤 ${clientId ?: UNKNOWN_CLIENT}",
        "📱 $kind",
    ).joinToString(SEPARATOR)

    /** 스캐너가 아주 긴 경로를 던져 로그를 덮는 것을 막는다. */
    private fun shortPath(): String {
        if (path.length <= MAX_PATH_LENGTH) {
            return path
        }
        return path.take(MAX_PATH_LENGTH) + "…"
    }

    /** 4xx 도 올린다 — 없는 경로를 훑는 침해 시도가 여기로 쌓인다. */
    fun needsAttention(): Boolean = status >= CLIENT_ERROR_STATUS

    private companion object {
        const val SEPARATOR: String = "  |  "
        const val CLIENT_ERROR_STATUS: Int = 400
        const val MAX_PATH_LENGTH: Int = 200
        const val UNKNOWN_CLIENT: String = "unknown"
    }
}
