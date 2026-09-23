package io.soo.bueongi.core.route.domain.marker.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoiType
import io.soo.bueongi.core.route.domain.marker.model.toMiniMapPercent
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.pathLengthMeters
import io.soo.bueongi.storage.db.core.safetyspot.model.sampleEveryMeters
import io.soo.bueongi.storage.db.core.safetyspot.store.GridSpatialIndex
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

@Component
class RouteMarkerBuilder(
    private val safetySpots: SafetySpotSpatialSource,
    private val markerEvents: MarkerEventPublisher? = null,
    private val visitEvents: RouteVisitEventPublisher? = null,
) {
    private val log: Logger = LoggerFactory.getLogger(RouteMarkerBuilder::class.java)

    fun buildMarkers(
        origin: GeoPoint,
        destination: GeoPoint,
        path: List<GeoPoint>,
    ): List<SafetySpotPoi> {
        alertIfIndexEmpty()
        publishVisitedPoints(origin, destination, path)

        val corridor: Double = SafetyScorer.DEFAULT_CORRIDOR_METERS
        val raw: ArrayList<SafetySpotPoi> = ArrayList()
        raw.add(SafetySpotPoi(type = SafetySpotPoiType.START, x = 0, y = 0, lat = origin.lat, lng = origin.lng))
        raw.add(SafetySpotPoi(type = SafetySpotPoiType.END, x = 0, y = 0, lat = destination.lat, lng = destination.lng))

        for ((markerType, safetySpotType) in MARKER_TYPES) {
            val typeIndex: GridSpatialIndex = safetySpots.index(safetySpotType)
            val alongPath: List<SafetySpotPoint> = typeIndex.withinCorridor(path, corridor)
            val shown: List<SafetySpotPoint> = spreadAlongPath(alongPath, MAX_MARKERS_PER_TYPE)
            for (safetySpot in shown) {
                raw.add(
                    SafetySpotPoi(
                        type = markerType,
                        x = 0,
                        y = 0,
                        lat = safetySpot.lat,
                        lng = safetySpot.lng,
                        name = safetySpot.name.orEmpty(),
                        purpose = safetySpot.purpose.orEmpty(),
                        cameraCount = safetySpot.cameraCount,
                        address = safetySpot.address.orEmpty(),
                        phone = safetySpot.phone.orEmpty(),
                    ),
                )
            }
        }

        return raw.toMiniMapPercent()
    }

    // 보안등 원천이 자치단체(instt_code)당 한 번만 응답해, 경로가 지나는 자치단체를 모두 짚어야 한다.
    private fun publishVisitedPoints(
        origin: GeoPoint,
        destination: GeoPoint,
        path: List<GeoPoint>,
    ) {
        val publisher: RouteVisitEventPublisher = visitEvents ?: return
        val route: List<GeoPoint> =
            if (path.size >= 2) {
                path
            } else {
                listOf(origin, destination)
            }
        val lengthMeters: Double = route.pathLengthMeters()
        val spanAtDefaultSpacing: Double = FILL_SAMPLE_SPACING_METERS * (MAX_FILL_SAMPLE_POINTS - 1)
        if (lengthMeters > spanAtDefaultSpacing) {
            log.info(
                "경로가 길어 지연 적재 표본 간격을 넓힙니다: 길이 {}m, 기준 간격 {}m, 표본 상한 {}개",
                lengthMeters.roundToInt(),
                FILL_SAMPLE_SPACING_METERS.roundToInt(),
                MAX_FILL_SAMPLE_POINTS,
            )
        }
        publisher.routeVisited(route.sampleEveryMeters(FILL_SAMPLE_SPACING_METERS, MAX_FILL_SAMPLE_POINTS))
    }

    private fun alertIfIndexEmpty() {
        val publisher: MarkerEventPublisher = markerEvents ?: return
        val totalLoaded: Int = MARKER_TYPES.sumOf { (_, safetySpotType) -> safetySpots.index(safetySpotType).size }
        if (totalLoaded > 0) {
            return
        }
        publisher.safetySpotIndexEmpty(
            "시설 인덱스 총 적재 0(적재 실패/부재) — /api/routes/safetySpots·/api/routes/safe-compare 의 " +
                "경로변 시설 마커를 생성할 수 없음. 데이터 적재/원천 확인 필요",
        )
    }

    private fun spreadAlongPath(
        alongPath: List<SafetySpotPoint>,
        limit: Int,
    ): List<SafetySpotPoint> {
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

        const val FILL_SAMPLE_SPACING_METERS: Double = 2_000.0

        // 점 하나가 최악의 경우 보안등 약 8회 외부 호출이라 한 요청의 쿼터 소모를 이 상한으로 막는다.
        const val MAX_FILL_SAMPLE_POINTS: Int = 12
    }
}
