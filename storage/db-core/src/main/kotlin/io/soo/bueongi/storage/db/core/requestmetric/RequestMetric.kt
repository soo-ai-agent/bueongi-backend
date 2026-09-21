package io.soo.bueongi.storage.db.core.requestmetric

import io.soo.bueongi.core.enums.RequestCaller

// PII 금지 — 좌표·검색어·토큰 원문은 넣지 않고, clientId 는 익명 식별자(X-Client-Id)만.
data class RequestMetric(
    val atEpochMs: Long,
    val method: String,
    // 동적 토큰 세그먼트는 `{token}` 으로 치환해 담는다(카디널리티 폭발 방지).
    val endpoint: String,
    val clientId: String?,
    val status: Int,
    val durationMs: Long,
    // 옛 기록에는 이 칸이 없어 기본값이 필요하다 — 그때는 사용자 요청만 기록했다.
    val caller: RequestCaller = RequestCaller.USER,
)
