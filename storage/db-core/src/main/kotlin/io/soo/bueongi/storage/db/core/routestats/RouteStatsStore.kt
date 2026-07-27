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

/**
 * 경로 추천 집계 저장소. 외부 API 접속 내역([io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore])과 동일 철학 —
 * Redis 주입 시 [RedisRouteStatsStore], 미주입(로컬/CI/MVP)이면 [InMemoryRouteStatsStore] 가 동일 계약으로 폴백한다.
 *
 * 최근 [ROUTE_STATS_CAP] 건만 유지하는 순환 버퍼다(오래된 것은 축출). 기록은 추천 흐름의 순수 부수효과이므로,
 * [record] 는 어떤 경우에도 예외를 밖으로 던지지 않는다(집계 실패가 추천 응답을 깨면 안 된다 — 내부에서 삼키고 로깅만).
 */
interface RouteStatsStore {
    /** 집계 1건 저장. **절대 예외를 던지지 않는다**(내부 try/catch 로 삼킨다). */
    fun record(log: RouteRecommendationLog)

    /** 최근순(최신 먼저) 조회. 최대 [limit] 건. */
    fun findRecent(limit: Int): List<RouteRecommendationLog>
}

/** 순환 버퍼 상한 — 최근 이만큼만 유지한다(Redis LTRIM / 인메모리 deque 축출). 24시간 트렌드 창을 넉넉히 담는 크기. */
const val ROUTE_STATS_CAP = 2000
