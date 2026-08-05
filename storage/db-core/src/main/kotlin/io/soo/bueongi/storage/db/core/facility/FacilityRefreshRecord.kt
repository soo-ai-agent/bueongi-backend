package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType
import org.springframework.stereotype.Component

/**
 * 한 종류의 마지막 갱신 기록. [replacedCount] 비null = 교체 성공(replaced), null = 수집 실패·0건으로 기존 유지(kept).
 * 기록 자체의 부재(홀더에 없음)는 "never"(아직 시도 없음 / 키 없는 원천)를 뜻한다 — 상태 판정은 조회측에서 한다.
 */
data class FacilityRefreshRecord(
    val atEpochMs: Long,
    val replacedCount: Int?,
)
