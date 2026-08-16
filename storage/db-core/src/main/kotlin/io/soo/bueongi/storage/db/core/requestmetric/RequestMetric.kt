package io.soo.bueongi.storage.db.core.requestmetric

/**
 * PII 금지 — 좌표·검색어·토큰 원문은 넣지 않고, [clientId] 는 익명 식별자(X-Client-Id)만.
 * 경로를 특정할 수 없는 요청이 있다.
 */
data class RequestMetric(
    val atEpochMs: Long,
    val method: String,
    /** 정규화된 경로 템플릿 — 동적 토큰 세그먼트는 `{token}` 치환(카디널리티 폭발 방지). */
    val endpoint: String,
    val clientId: String?,
    val status: Int,
    val durationMs: Long,
)
