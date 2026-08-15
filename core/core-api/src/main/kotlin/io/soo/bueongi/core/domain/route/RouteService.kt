package io.soo.bueongi.core.domain.route

import io.soo.bueongi.storage.db.core.facility.SpatialMath
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

@Service
class RouteService(
    private val routeStatsStore: RouteStatsStore? = null,
) {

    fun compare(query: RouteQuery): List<RouteOption> {
        routeStatsStore.recordRecommendation("compare", query)
        return buildRoutes(query)
    }

    fun buildRoutes(query: RouteQuery): List<RouteOption> {
        val straightMeters: Double = SpatialMath.haversineMeters(query.origin, query.destination)
        return RouteType.entries.map { type ->
            val meters: Double = straightMeters * type.distanceFactor
            RouteOption(
                id = type.id,
                name = type.displayName,
                time = "${RouteFormat.walkMinutes(meters)}분",
                dist = RouteFormat.formatDistance(meters),
                desc = type.desc,
                tags = type.tags,
                type = type,
            )
        }
    }
}

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
