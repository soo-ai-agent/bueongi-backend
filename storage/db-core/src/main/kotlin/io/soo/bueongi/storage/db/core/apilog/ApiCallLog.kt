package io.soo.bueongi.storage.db.core.apilog

import io.soo.bueongi.core.enums.ApiProvider

/** PII 금지 — 검색어·좌표·토큰 등 사용자 입력은 [detail] 에도 넣지 않는다. */
data class ApiCallLog(
    val atEpochMs: Long,
    /** 저장 포맷이라 enum 대신 [ApiProvider.name] 문자열. */
    val provider: String,
    val endpoint: String,
    val success: Boolean,
    val durationMs: Long,
    val detail: String? = null,
)
