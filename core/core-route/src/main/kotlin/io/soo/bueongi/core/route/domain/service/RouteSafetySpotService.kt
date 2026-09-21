package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.SafetySpotSummary
import io.soo.bueongi.core.route.domain.model.RouteSafetySpots
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoiType

import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.stereotype.Service

@Service
class RouteSafetySpotService(
    private val markerBuilder: RouteMarkerBuilder,
) {

    fun alongRoute(query: RouteQuery): RouteSafetySpots {
        val origin: GeoPoint = query.origin
        val destination: GeoPoint = query.destination

        val straightLinePath: List<GeoPoint> = listOf(origin, destination)
        val pois: List<SafetySpotPoi> = markerBuilder.buildMarkers(origin, destination, straightLinePath)

        val summary = SafetySpotSummary(
            cctv = pois.count { it.type == SafetySpotPoiType.CCTV },
            bell = pois.count { it.type == SafetySpotPoiType.BELL },
            convenienceStore = pois.count { it.type == SafetySpotPoiType.STORE },
            police = pois.count { it.type == SafetySpotPoiType.POLICE },
            safehouse = pois.count { it.type == SafetySpotPoiType.SAFEHOUSE },
            lamp = pois.count { it.type == SafetySpotPoiType.STREET_LAMP },
            securityLamp = pois.count { it.type == SafetySpotPoiType.SECURITY_LAMP },
            pedestrianSignal = pois.count { it.type == SafetySpotPoiType.PEDESTRIAN_SIGNAL },
            total = pois.count { it.type in SAFETY_TYPES },
        )
        return RouteSafetySpots(pois = pois, summary = summary)
    }

    private companion object {
        val SAFETY_TYPES: Set<SafetySpotPoiType> = setOf(
            SafetySpotPoiType.CCTV,
            SafetySpotPoiType.BELL,
            SafetySpotPoiType.STORE,
            SafetySpotPoiType.POLICE,
            SafetySpotPoiType.SAFEHOUSE,
            SafetySpotPoiType.STREET_LAMP,
            SafetySpotPoiType.SECURITY_LAMP,
            SafetySpotPoiType.PEDESTRIAN_SIGNAL,
        )
    }
}
