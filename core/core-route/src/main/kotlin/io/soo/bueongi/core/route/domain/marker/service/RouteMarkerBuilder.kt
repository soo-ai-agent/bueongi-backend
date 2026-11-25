package io.soo.bueongi.core.route.domain.marker.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoiType
import io.soo.bueongi.core.route.domain.marker.model.toMiniMapPercent
import io.soo.bueongi.core.route.domain.marker.model.toPoi
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.support.util.pickEvenly
import org.springframework.stereotype.Component

@Component
class RouteMarkerBuilder(
    private val safetySpots: SafetySpotSpatialSource,
) {
    fun build(origin: GeoPoint, destination: GeoPoint, path: List<GeoPoint>): List<SafetySpotPoi> {
        val corridor: Double = SafetyScorer.DEFAULT_CORRIDOR_METERS
        val raw: ArrayList<SafetySpotPoi> = ArrayList()
        raw.add(SafetySpotPoi(type = SafetySpotPoiType.START, x = 0, y = 0, lat = origin.lat, lng = origin.lng))
        raw.add(SafetySpotPoi(type = SafetySpotPoiType.END, x = 0, y = 0, lat = destination.lat, lng = destination.lng))

        for ((markerType, safetySpotType) in MARKER_TYPES) {
            val alongPath: List<SafetySpotPoint> = safetySpots.findWithinCorridor(safetySpotType, path, corridor)
            val shown: List<SafetySpotPoint> = alongPath.pickEvenly(MAX_MARKERS_PER_TYPE)
            for (safetySpot in shown) {
                raw.add(safetySpot.toPoi(markerType))
            }
        }

        return raw.toMiniMapPercent()
    }

    private companion object {
        val MARKER_TYPES: List<Pair<SafetySpotPoiType, SafetySpotType>> =
            listOf(
                SafetySpotPoiType.CCTV to SafetySpotType.CCTV,
                SafetySpotPoiType.SAFEHOUSE to SafetySpotType.SAFEHOUSE,
                SafetySpotPoiType.BELL to SafetySpotType.BELL,
                SafetySpotPoiType.POLICE to SafetySpotType.POLICE,
                SafetySpotPoiType.STREET_LAMP to SafetySpotType.STREET_LAMP,
                SafetySpotPoiType.SECURITY_LAMP to SafetySpotType.SECURITY_LAMP,
                SafetySpotPoiType.PEDESTRIAN_SIGNAL to SafetySpotType.PEDESTRIAN_SIGNAL,
            )

        const val MAX_MARKERS_PER_TYPE: Int = 12
    }
}
