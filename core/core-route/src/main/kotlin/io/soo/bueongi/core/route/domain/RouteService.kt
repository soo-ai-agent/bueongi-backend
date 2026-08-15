package io.soo.bueongi.core.route.domain

import io.soo.bueongi.support.util.DistanceFormat
import io.soo.bueongi.core.enums.RouteType

import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

/**
 * Tmap 없이 만드는 안내용 경로. 키가 없거나 Tmap 이 전멸했을 때 [SafeRouteService] 가 부른다.
 * 실제 길이 아니라 **직선거리에 유형별 계수를 곱한 추정치**라 점수·좌표·마커를 담지 않는다.
 */
@Service
class RouteService(
    private val routeStatsStore: RouteStatsStore? = null,
) {

    fun compare(query: RouteQuery): List<RouteOption> {
        routeStatsStore.recordRecommendation("compare", query)
        return buildRoutes(query)
    }

    fun buildRoutes(query: RouteQuery): List<RouteOption> {
        val straightMeters: Double = query.origin.distanceMetersTo(query.destination)
        // 유형마다 우회 계수가 다르다(안심 1.18 · 주요 1.05 · 최단 0.92) — 3개를 한 번에 만든다.
        return RouteType.entries.map { type ->
            val meters: Double = straightMeters * type.distanceFactor
            RouteOption(
                id = type.id,
                name = type.displayName,
                time = "${DistanceFormat.walkMinutes(meters)}분",
                dist = DistanceFormat.formatDistance(meters),
                desc = type.desc,
                tags = type.tags,
                type = type,
            )
        }
    }
}

/** 추천 집계. 저장소가 없으면(Redis 미설정) 조용히 건너뛴다 — 집계 실패가 경로 응답을 막지 않는다. */
internal fun RouteStatsStore?.recordRecommendation(endpoint: String, query: RouteQuery) {
    if (this == null) {
        return
    }
    record(
        RouteRecommendationLog(
            atEpochMs = System.currentTimeMillis(),
            endpoint = endpoint,
            destinationName = query.destinationName,
            preference = query.safetyPreference,
        ),
    )
}
