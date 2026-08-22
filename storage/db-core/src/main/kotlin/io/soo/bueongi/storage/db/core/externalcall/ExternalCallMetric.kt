package io.soo.bueongi.storage.db.core.externalcall

import io.soo.bueongi.core.enums.ExternalApiProvider

/**
 * 우리가 외부 API 를 한 번 부른 기록.
 *
 * 비밀 금지 — [endpoint] 는 질의 문자열을 뗀 경로만 담는다(serviceKey·appKey 가 질의에 실린다).
 */
data class ExternalCallMetric(
    val atEpochMs: Long,
    val provider: ExternalApiProvider,
    val method: String,
    /** 질의 문자열을 뗀 경로. 페이지 번호처럼 값이 매번 다른 것은 경로에 없다. */
    val endpoint: String,
    /** 연결 자체가 실패하면 응답 코드가 없어 0 이다. */
    val status: Int,
    val durationMs: Long,
    // 성공했으면 실패 사유가 없어 null 이 가능함
    val failureReason: String?,
)
