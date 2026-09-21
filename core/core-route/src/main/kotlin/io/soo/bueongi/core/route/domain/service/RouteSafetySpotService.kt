package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.SafetySpotSummary
import io.soo.bueongi.core.route.domain.model.RouteSafetySpots
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoiType

import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.stereotype.Service

/**
 * 경로와 무관하게 "출발지–도착지 사이"의 시설만 훑어 주는 가벼운 조회.
 * 경로 비교 전에 화면이 먼저 시설을 보여줄 때 쓴다(Tmap 호출 없음).
 */
@Service
class RouteSafetySpotService(
    private val markerBuilder: RouteMarkerBuilder,
) {

    fun alongRoute(query: RouteQuery): RouteSafetySpots {
        val origin: GeoPoint = query.origin
        val destination: GeoPoint = query.destination

        // 경로를 모르므로 출발–도착 두 점을 잇는 직선을 경로로 삼는다.
        val pois: List<SafetySpotPoi> = markerBuilder.buildMarkers(origin, destination, listOf(origin, destination))

        // 요약은 마커를 다시 세어 만든다 — 지도에 찍힌 것과 숫자가 어긋나지 않게.
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
