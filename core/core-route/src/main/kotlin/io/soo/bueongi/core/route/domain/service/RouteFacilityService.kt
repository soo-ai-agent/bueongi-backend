package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.route.domain.model.FacilitySummary
import io.soo.bueongi.core.route.domain.model.RouteFacilities
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.core.route.marker.model.FacilityPoi
import io.soo.bueongi.core.route.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.enums.FacilityPoiType

import io.soo.bueongi.storage.db.core.facility.model.GeoPoint
import org.springframework.stereotype.Service

/**
 * 경로와 무관하게 "출발지–도착지 사이"의 시설만 훑어 주는 가벼운 조회.
 * 경로 비교 전에 화면이 먼저 시설을 보여줄 때 쓴다(Tmap 호출 없음).
 */
@Service
class RouteFacilityService(
    private val markerBuilder: RouteMarkerBuilder,
) {

    fun alongRoute(query: RouteQuery): RouteFacilities {
        val origin: GeoPoint = query.origin
        val destination: GeoPoint = query.destination

        // 경로를 모르므로 출발–도착 두 점을 잇는 직선을 경로로 삼는다.
        val pois: List<FacilityPoi> = markerBuilder.buildMarkers(origin, destination, listOf(origin, destination))

        // 요약은 마커를 다시 세어 만든다 — 지도에 찍힌 것과 숫자가 어긋나지 않게.
        val summary: FacilitySummary = FacilitySummary(
            cctv = pois.count { it.type == FacilityPoiType.CCTV },
            bell = pois.count { it.type == FacilityPoiType.BELL },
            convenienceStore = pois.count { it.type == FacilityPoiType.STORE },
            police = pois.count { it.type == FacilityPoiType.POLICE },
            safehouse = pois.count { it.type == FacilityPoiType.SAFEHOUSE },
            lamp = pois.count { it.type == FacilityPoiType.LAMP },
            pedestrianSignal = pois.count { it.type == FacilityPoiType.PEDESTRIAN_SIGNAL },
            total = pois.count { it.type in SAFETY_TYPES },
        )
        return RouteFacilities(pois = pois, summary = summary)
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
