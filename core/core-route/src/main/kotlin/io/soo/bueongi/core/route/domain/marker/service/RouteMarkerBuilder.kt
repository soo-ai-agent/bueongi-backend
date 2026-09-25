package io.soo.bueongi.core.route.domain.marker.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoiType
import io.soo.bueongi.core.route.domain.marker.model.toMiniMapPercent
import io.soo.bueongi.core.route.domain.marker.model.toPoi
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.GridSpatialIndex
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Component

@Component
class RouteMarkerBuilder(private val safetySpots: SafetySpotSpatialSource) {
    fun buildMarkers(origin: GeoPoint, destination: GeoPoint, path: List<GeoPoint>): List<SafetySpotPoi> {
        val corridor: Double = SafetyScorer.DEFAULT_CORRIDOR_METERS
        val raw: ArrayList<SafetySpotPoi> = ArrayList()
        raw.add(SafetySpotPoi(type = SafetySpotPoiType.START, x = 0, y = 0, lat = origin.lat, lng = origin.lng))
        raw.add(SafetySpotPoi(type = SafetySpotPoiType.END, x = 0, y = 0, lat = destination.lat, lng = destination.lng))

        for ((markerType, safetySpotType) in MARKER_TYPES) {
            val typeIndex: GridSpatialIndex = safetySpots.index(safetySpotType)
            val alongPath: List<SafetySpotPoint> = typeIndex.withinCorridor(path, corridor)
            val shown: List<SafetySpotPoint> = spreadAlongPath(alongPath, MAX_MARKERS_PER_TYPE)
            for (safetySpot in shown) {
                raw.add(safetySpot.toPoi(markerType))
            }
        }

        return raw.toMiniMapPercent()
    }

    private fun spreadAlongPath(alongPath: List<SafetySpotPoint>, limit: Int): List<SafetySpotPoint> {
        if (alongPath.size <= limit) {
            return alongPath
        }
        val picked: ArrayList<SafetySpotPoint> = ArrayList(limit)
        for (slot in 0 until limit) {
            picked.add(alongPath[slot * alongPath.size / limit])
        }
        return picked
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
