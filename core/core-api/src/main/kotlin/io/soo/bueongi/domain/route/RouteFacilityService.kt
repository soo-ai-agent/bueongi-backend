package io.soo.bueongi.domain.route

import org.springframework.stereotype.Service

/**
 * 경로상 안심시설(CCTV/비상벨/편의점/파출소/안심집) 서비스 — POST /api/routes/facilities.
 *
 * 정직 원칙: 밀도 추정으로 개수를 지어내지 않고 **회랑 30m 실측만** 집계한다. 이 엔드포인트는
 * 외부 라우팅(Tmap) 없이 stateless 로 동작하므로 실제 경로를 출발→도착 **직선으로 근사**하고,
 * 그 직선 회랑의 실 시설을 [RouteMarkerBuilder] 로 질의한다(safe-compare 마커와 같은 실측 소스·로직).
 * 두 화면이 같은 근거를 공유하므로 시설 집합이 엔드포인트마다 어긋나지 않는다.
 *
 * 편의점(store)은 사용자 결정으로 **지도에서 제외**되어 마커를 생성하지 않으므로 요약도 항상 0 이다
 * (프론트 계약상 store 필드는 유지하되 0 → 칩 숨김). 실 시설이 회랑에 없는 유형도 0 으로 남는다.
 * 요약(summary)은 생성된 POI 를 그대로 집계하므로 지도 표시와 항상 일치한다.
 */
@Service
class RouteFacilityService(
    private val markerBuilder: RouteMarkerBuilder,
) {

    /** 출발→도착 직선 회랑의 실측 안심시설 POI + 집계 요약을 만든다. */
    fun alongRoute(query: RouteQuery): FacilitiesResponse {
        val origin = query.origin
        val destination = query.destination

        // 실측 마커(출발/도착 + 회랑 내 실 시설). 가로등(lamp)은 이 엔드포인트 요약 대상이 아니고
        // 프론트 /facilities 파서도 수용하지 않으므로 제외한다(가로등 마커 자체는 safe-compare 에서 노출).
        val pois = markerBuilder.buildMarkers(origin, destination, listOf(origin, destination))
            .filterNot { it.type == FacilityPoiType.LAMP }

        // 요약은 생성된 POI 를 그대로 집계(start/end 제외) — 표시와 불일치 불가. store 는 지도 제외(마커 미생성) → 항상 0.
        val summary = FacilitySummary(
            cctv = pois.count { it.type == FacilityPoiType.CCTV },
            bell = pois.count { it.type == FacilityPoiType.BELL },
            store = pois.count { it.type == FacilityPoiType.STORE },
            police = pois.count { it.type == FacilityPoiType.POLICE },
            safehouse = pois.count { it.type == FacilityPoiType.SAFEHOUSE },
            total = pois.count { it.type in SAFETY_TYPES },
        )
        return FacilitiesResponse(pois = pois, summary = summary)
    }

    private companion object {
        val SAFETY_TYPES = setOf(
            FacilityPoiType.CCTV,
            FacilityPoiType.BELL,
            FacilityPoiType.STORE,
            FacilityPoiType.POLICE,
            FacilityPoiType.SAFEHOUSE,
        )
    }
}
