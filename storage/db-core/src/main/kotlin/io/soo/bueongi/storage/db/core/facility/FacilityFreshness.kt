package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType

/**
 * 안심시설 데이터 신선도(P0-3). 파이프라인 산출물 manifest.json 의 생성 시점 —
 * "이 점수/마커가 언제 기준의 공공데이터로 계산됐는가"를 사용자에게 표기하기 위한 메타.
 */
data class FacilityFreshness(
    /** manifest.generatedAt(ISO-8601). 미상이면 null. */
    val generatedAt: String? = null,
    /** manifest.version(예: "2026-06-19"). 미상이면 null. */
    val version: String? = null,
)
