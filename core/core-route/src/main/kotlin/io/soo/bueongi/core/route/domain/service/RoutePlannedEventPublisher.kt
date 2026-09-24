package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.common.event.RoutePlannedEvent
import io.soo.bueongi.core.common.event.RoutePoint
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class RoutePlannedEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun routePlanned(points: List<GeoPoint>) {
        val visited: ArrayList<RoutePoint> = ArrayList(points.size)
        for (point in points) {
            visited.add(RoutePoint(lat = point.lat, lng = point.lng))
        }
        events.publishEvent(RoutePlannedEvent(visited))
    }
}
