package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.common.event.RouteVisitedEvent
import io.soo.bueongi.core.common.event.VisitedPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class RouteVisitEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun routeVisited(points: List<GeoPoint>) {
        val visited: ArrayList<VisitedPoint> = ArrayList(points.size)
        for (point in points) {
            visited.add(VisitedPoint(lat = point.lat, lng = point.lng))
        }
        events.publishEvent(RouteVisitedEvent(visited))
    }
}
