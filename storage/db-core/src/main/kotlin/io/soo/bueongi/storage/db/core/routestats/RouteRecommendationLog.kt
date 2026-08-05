package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.SafetyPreference

/**
 * 경로 추천 요청 1건의 집계 기록. 관리자 페이지의 "추천 통계"에 그대로 집계되어 노출된다 —
 * 언제([atEpochMs]) 어떤 종류([endpoint])의 추천 요청이, 어떤 안심강도 선호([preference])로,
 * 어느 목적지([destinationName])를 대상으로 들어왔는지.
 * PII 는 최소화한다 — 좌표·토큰·검색어는 담지 않고, 목적지 표시명만(사용자가 고른 공개 지명) 담는다.
 */
data class RouteRecommendationLog(
    /** 요청 시각(epoch ms). 최근순 정렬·시간대 트렌드 집계용. */
    val atEpochMs: Long,
    /** 추천 엔드포인트 종류: compare | safe-compare. */
    val endpoint: String,
    /** 목적지 표시명(선택). 빈/누락이면 null — 상위 목적지 집계에서 제외된다. */
    val destinationName: String?,
    /** 안심강도 선호. JSON(저장/집계)에는 requestValue(balanced|safe|safest)로 직렬화된다. */
    val preference: SafetyPreference,
)
