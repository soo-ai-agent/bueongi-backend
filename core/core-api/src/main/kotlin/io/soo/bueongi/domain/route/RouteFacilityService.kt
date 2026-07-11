package io.soo.bueongi.domain.route

import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

/**
 * 경로상 안심시설(CCTV/비상벨/편의점/파출소) 서비스.
 *
 * 출발→도착 직선거리와 선택 경로 유형(safe/main/fast)의 시설 밀도로 POI 개수를 결정하고, 직선을 따라
 * 결정론적으로 배치한다(같은 입력엔 항상 같은 결과). 시설 개수는 [FacilityCountEstimator] 단일 출처에서
 * 얻으므로 safe-compare 마커의 개수와 어긋나지 않는다. 요약(summary)은 생성된 POI 를 실제로 집계하므로
 * 지도에 표시되는 것과 항상 일치한다 — 프론트 summarizeSafetyFacilities 의 "보이는 것만 센다" 원칙과 동형.
 *
 * 외부 공공데이터(실 CCTV/비상벨 위치) 연동 시 POI 생성부만 교체하면 되고 응답 계약은 동일하다.
 */
@Service
class RouteFacilityService {

    /** 선택 경로 유형 기준 경로상 안심시설 POI + 집계 요약을 만든다. */
    fun alongRoute(query: RouteQuery): FacilitiesResponse {
        val origin = query.origin
        val destination = query.destination
        val counts = FacilityCountEstimator.estimate(origin, destination, query.routeType)

        val pois = buildList {
            add(endpoint("start", origin))
            addFacilities("cctv", counts.cctv, origin, destination)
            addFacilities("bell", counts.bell, origin, destination)
            addFacilities("safehouse", counts.safehouse, origin, destination)
            addFacilities("store", counts.store, origin, destination)
            addFacilities("police", counts.police, origin, destination)
            add(endpoint("end", destination))
        }

        // 요약은 생성된 POI 를 그대로 집계(start/end 제외) — 표시와 불일치 불가.
        val summary = FacilitySummary(
            cctv = pois.count { it.type == "cctv" },
            bell = pois.count { it.type == "bell" },
            store = pois.count { it.type == "store" },
            police = pois.count { it.type == "police" },
            safehouse = pois.count { it.type == "safehouse" },
            total = pois.count { it.type in SAFETY_TYPES },
        )
        return FacilitiesResponse(pois = pois, summary = summary)
    }

    private fun MutableList<FacilityPoi>.addFacilities(
        type: String,
        count: Int,
        origin: GeoPoint,
        destination: GeoPoint,
    ) {
        for (i in 0 until count) {
            // 경로(직선)를 따라 균등 분포 + 유형별 오프셋으로 겹침 방지. 결정론적(인덱스 기반).
            val t = (i + 1).toDouble() / (count + 1)
            val typeOffset = TYPE_LATERAL[type] ?: 0.0
            val lat = origin.lat + (destination.lat - origin.lat) * t
            val lng = origin.lng + (destination.lng - origin.lng) * t
            // 정규화 좌표(0~100): 좌하단(출발)→우상단(도착) 대각선 경로 + 유형별 측면 분리.
            val x = (8 + 84 * t + typeOffset * 6).roundToInt().coerceIn(0, 100)
            val y = (80 - 60 * t - typeOffset * 6).roundToInt().coerceIn(0, 100)
            add(FacilityPoi(type = type, x = x, y = y, lat = lat, lng = lng))
        }
    }

    private fun endpoint(type: String, point: GeoPoint): FacilityPoi {
        // MapMock 정규화 좌표(0~100): 출발지는 좌하단, 도착지는 우상단에 고정 배치한다.
        val x: Int
        val y: Int
        if (type == "start") {
            x = 12
            y = 84
        } else {
            x = 88
            y = 16
        }
        return FacilityPoi(type = type, x = x, y = y, lat = point.lat, lng = point.lng)
    }
}

private val SAFETY_TYPES = setOf("cctv", "bell", "store", "police", "safehouse")

/** 유형별 측면 오프셋(겹침 방지용, 결정론적). */
private val TYPE_LATERAL = mapOf(
    "cctv" to 1.0,
    "bell" to -1.0,
    "store" to 0.5,
    "police" to -0.5,
    "safehouse" to 1.5,
)
