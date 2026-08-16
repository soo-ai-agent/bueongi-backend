package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.RouteTypeDisplay
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.support.util.formatDistance
import io.soo.bueongi.support.util.walkMinutes
import io.soo.bueongi.core.enums.RouteType

import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

/** 실제 길이 아니라 직선거리 추정치라 점수·좌표·마커를 담지 않는다. */
@Service
class RouteService(
    private val routeTypeCatalog: RouteTypeCatalog,
    // 통계 없이도 경로 생성은 돌아야 해서 선택 주입이다.
    private val routeStatsStore: RouteStatsStore? = null,
) {

    fun compare(query: RouteQuery): List<RouteOption> {
        routeStatsStore.recordRecommendation("compare", query)
        return buildStraightLineRoutes(query)
    }

    fun buildStraightLineRoutes(query: RouteQuery): List<RouteOption> {
        val straightMeters: Double = query.origin.distanceMetersTo(query.destination)
        val options: ArrayList<RouteOption> = ArrayList()
        for (type in RouteType.entries) {
            val display: RouteTypeDisplay = routeTypeCatalog.displayOf(type)
            val meters: Double = straightMeters * display.distanceFactor
            options.add(
                RouteOption(
                    id = display.id,
                    name = display.name,
                    time = "${walkMinutes(meters)}분",
                    dist = formatDistance(meters),
                    desc = display.desc,
                    tags = display.tags,
                    type = type,
                ),
            )
        }
        return options
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
