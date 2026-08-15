package io.soo.bueongi.storage.db.core.routestats

interface RouteStatsStore {
    /** 계약: 절대 예외를 던지지 않는다(집계 실패가 추천 응답을 깨면 안 됨 — 내부에서 삼킨다). */
    fun record(log: RouteRecommendationLog)

    fun findRecent(limit: Int): List<RouteRecommendationLog>
}

const val ROUTE_STATS_CAP: Int = 2000
