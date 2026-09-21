package io.soo.bueongi.storage.db.core.externalcall

import io.soo.bueongi.core.enums.ExternalApiProvider

// 비밀 금지 — endpoint 는 질의 문자열을 뗀 경로만 담는다(serviceKey·appKey 가 질의에 실린다).
data class ExternalCallMetric(
    val atEpochMs: Long,
    val provider: ExternalApiProvider,
    val method: String,
    val endpoint: String,
    // 연결 자체가 실패하면 응답 코드가 없어 0 이다.
    val status: Int,
    val durationMs: Long,
    val failureReason: String?,
)
