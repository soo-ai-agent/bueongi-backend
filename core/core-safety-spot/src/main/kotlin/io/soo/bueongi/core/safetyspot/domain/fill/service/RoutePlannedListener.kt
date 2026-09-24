package io.soo.bueongi.core.safetyspot.domain.fill.service

import io.soo.bueongi.core.common.event.RoutePlannedEvent
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class RoutePlannedListener(
    private val trigger: SafetySpotFillTrigger,
) {
    @EventListener
    fun onRoutePlanned(event: RoutePlannedEvent) {
        val points: ArrayList<GeoPoint> = ArrayList(event.points.size)
        for (point in event.points) {
            points.add(GeoPoint(lat = point.lat, lng = point.lng))
        }
        trigger.fillAround(points)
    }
}
