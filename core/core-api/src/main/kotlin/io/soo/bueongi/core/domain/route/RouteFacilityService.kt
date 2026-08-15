package io.soo.bueongi.core.domain.route

import io.soo.bueongi.core.api.controller.v1.response.FacilitiesResponse

import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Service

@Service
class RouteFacilityService(
    private val markerBuilder: RouteMarkerBuilder,
) {

    fun alongRoute(query: RouteQuery): FacilitiesResponse {
        val origin: GeoPoint = query.origin
        val destination: GeoPoint = query.destination

        val pois: List<FacilityPoi> = markerBuilder.buildMarkers(origin, destination, listOf(origin, destination))

        val summary: FacilitySummary = FacilitySummary(
            cctv = pois.count { it.type == FacilityPoiType.CCTV },
            bell = pois.count { it.type == FacilityPoiType.BELL },
            store = pois.count { it.type == FacilityPoiType.STORE },
            police = pois.count { it.type == FacilityPoiType.POLICE },
            safehouse = pois.count { it.type == FacilityPoiType.SAFEHOUSE },
            lamp = pois.count { it.type == FacilityPoiType.LAMP },
            pedestrianSignal = pois.count { it.type == FacilityPoiType.PEDESTRIAN_SIGNAL },
            total = pois.count { it.type in SAFETY_TYPES },
        )
        return FacilitiesResponse(pois = pois, summary = summary)
    }

    private companion object {
        val SAFETY_TYPES: Set<FacilityPoiType> = setOf(
            FacilityPoiType.CCTV,
            FacilityPoiType.BELL,
            FacilityPoiType.STORE,
            FacilityPoiType.POLICE,
            FacilityPoiType.SAFEHOUSE,
            FacilityPoiType.LAMP,
            FacilityPoiType.PEDESTRIAN_SIGNAL,
        )
    }
}
