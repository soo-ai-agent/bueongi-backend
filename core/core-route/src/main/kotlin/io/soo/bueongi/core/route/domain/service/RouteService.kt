package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.domain.model.RouteTypeDisplay
import io.soo.bueongi.support.util.formatDistance
import io.soo.bueongi.support.util.walkMinutes
import org.springframework.stereotype.Service

@Service
class RouteService(
    private val routeTypeCatalog: RouteTypeCatalog,
) {
    fun buildStraightLineRoutes(query: RouteRequest): List<RouteOption> {
        val straightMeters: Double = query.origin.distanceMetersTo(query.destination)
        val options: ArrayList<RouteOption> = ArrayList()
        for (type in RouteType.entries) {
            val display: RouteTypeDisplay = routeTypeCatalog.displayOf(type)
            val meters: Double = straightMeters * display.distanceFactor
            options.add(
                RouteOption(
                    id = type.requestId,
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
