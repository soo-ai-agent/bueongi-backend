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
    val responseBytes: Long,
    // 응답 본문이 없는 상태(204·304)에서는 형식도 없어 null 이 가능함
    val contentType: String?,
) {
    /** 본문 자체는 남기지 않는다 — 좌표·검색 결과·공유 토큰이 들어 있다. 크기와 형식만 남긴다. */
    fun format(): String =
        "$method ${shortPath()} $status (${durationMs}ms) " +
            "client=${clientId ?: UNKNOWN_CLIENT} ip=$address out=${responseBytes}B${typeSuffix()}"

    private fun typeSuffix(): String {
        val type: String = contentType ?: return ""
        return " type=${type.substringBefore(';')}"
    }

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
        const val CLIENT_ERROR_STATUS: Int = 400
        const val MAX_PATH_LENGTH: Int = 200
        const val UNKNOWN_CLIENT: String = "unknown"
    }
}
