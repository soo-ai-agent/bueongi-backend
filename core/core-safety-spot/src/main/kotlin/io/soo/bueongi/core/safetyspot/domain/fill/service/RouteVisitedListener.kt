package io.soo.bueongi.core.safetyspot.domain.fill.service

import io.soo.bueongi.core.common.event.RouteVisitedEvent
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** 경로가 지나간 곳의 안심시설을 채운다. 채우기 자체는 트리거가 자기 스레드에서 한다. */
@Component
class RouteVisitedListener(
    private val trigger: SafetySpotFillTrigger,
) {

    @EventListener
    fun onRouteVisited(event: RouteVisitedEvent) {
        val points: ArrayList<GeoPoint> = ArrayList(event.points.size)
        for (point in event.points) {
            points.add(GeoPoint(lat = point.lat, lng = point.lng))
        }
        trigger.fillAround(points)
    }
}
