package io.soo.bueongi.storage.db.core.routestats

import org.slf4j.LoggerFactory

/**
 * Redis 미주입(로컬/CI/MVP 기본) 시 동작하는 인메모리 추천 집계 저장소. 접속 내역 저장소의 인메모리 폴백과 동일 철학 —
 * 외부 인프라 없이 동일 [RouteStatsStore] 계약으로 동작·테스트된다. 단일 인스턴스 가정(재시작 시 휘발).
 *
 * 최신이 앞([addFirst])인 capped deque — 상한([ROUTE_STATS_CAP]) 초과 시 가장 오래된 것을 뒤에서 축출한다.
 */
class InMemoryRouteStatsStore : RouteStatsStore {

    private val logger = LoggerFactory.getLogger(InMemoryRouteStatsStore::class.java)
    private val logs: ArrayDeque<RouteRecommendationLog> = ArrayDeque<RouteRecommendationLog>()
    private val lock: Any = Any()

    override fun record(log: RouteRecommendationLog) {
        // 집계 실패가 추천 응답을 깨면 안 된다 — 삼키고 경고만(계약: record 는 예외를 밖으로 던지지 않는다).
        try {
            synchronized(lock) {
                logs.addFirst(log)
                while (logs.size > ROUTE_STATS_CAP) {
                    logs.removeLast()
                }
            }
        } catch (e: Exception) {
            logger.warn("추천 통계 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(limit: Int): List<RouteRecommendationLog> {
        val snapshot: List<RouteRecommendationLog> = synchronized(lock) { logs.toList() } // 이미 최신순(addFirst)
        return snapshot.take(limit.coerceAtLeast(0))
    }
}
