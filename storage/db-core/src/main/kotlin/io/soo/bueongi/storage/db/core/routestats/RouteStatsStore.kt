package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.SafetyPreference


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
const val ROUTE_STATS_CAP: Int = 2000
