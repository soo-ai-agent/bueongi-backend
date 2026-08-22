package io.soo.bueongi.api.config

import io.soo.bueongi.core.enums.RequestCaller

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
    val caller: RequestCaller,
) {
    /**
     * 맨 앞이 방향이다 — 우리가 받은 요청인지 우리가 보낸 호출인지가 기호가 아니라 낱말로 갈린다.
     * 뒤이어 상대(부른 IP) · 상태+메서드 · 경로 · 소요시간 · 부가 순으로, 외부 송신 줄
     * ([ExternalCallLogLine])과 같은 자리에 같은 뜻이 오게 맞춘다. 응답 본문·크기는 남기지 않는다.
     */
    fun format(): String = listOf(
        padColumn(directionAndCaller(), DIRECTION_COLUMN_WIDTH),
        padColumn(address, COUNTERPART_COLUMN_WIDTH),
        padColumn("$status $method", STATUS_COLUMN_WIDTH),
        shortPath(),
        "${durationMs}ms".padStart(DURATION_COLUMN_WIDTH),
        "👤 ${clientId ?: UNKNOWN_CLIENT}",
        "📱 $kind",
    ).joinToString(LOG_COLUMN_SEPARATOR)

    /**
     * 방향 바로 옆에 부른 쪽을 적는다 — 관리자 화면의 조회가 사용자 트래픽으로 읽히면
     * 지표를 잘못 읽게 된다. 와이어 값(admin·user)은 그대로 두고 여기서만 꾸민다.
     */
    private fun directionAndCaller(): String = when (caller) {
        RequestCaller.ADMIN -> "$INBOUND_DIRECTION 관리자"
        RequestCaller.USER -> "$INBOUND_DIRECTION 사용자"
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
