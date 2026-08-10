package io.soo.bueongi.storage.db.core.adevent

data class AdEvent(
    val atEpochMs: Long,
    /** 광고 지면: interstitial | native — 허용값 검증은 요청 DTO 계층에서 끝난다. */
    val placement: String,
)
