package io.soo.bueongi.storage.db.core.requestmetric

import io.soo.bueongi.core.enums.RequestCaller

/**
 * PII 금지 — 좌표·검색어·토큰 원문은 넣지 않고, [clientId] 는 익명 식별자(X-Client-Id)만.
 */
data class RequestMetric(
    val atEpochMs: Long,
    val method: String,
    /** 정규화된 경로 템플릿 — 동적 토큰 세그먼트는 `{token}` 치환(카디널리티 폭발 방지). */
    val endpoint: String,
    // 익명 식별자를 보내지 않으면 null 이 가능함
    val clientId: String?,
    val status: Int,
    val durationMs: Long,
    /** 옛 기록에는 이 칸이 없다 — 읽다 깨지지 않게 사용자로 본다(그때는 사용자만 기록했다). */
    val caller: RequestCaller = RequestCaller.USER,
)
