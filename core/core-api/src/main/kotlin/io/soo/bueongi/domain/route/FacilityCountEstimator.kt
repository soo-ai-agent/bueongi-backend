package io.soo.bueongi.domain.route

import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 경로 유형·거리 기반 안심시설 개수 추정의 단일 출처.
 *
 * `/api/routes/facilities`(경로상 시설 목록)와 safe-compare 마커의 시설 개수가 **같은 근거**로 결정되도록,
 * 유형별 밀도(개/km)×보행거리 + 추천(safe) 경로 최소 보장(floor) + 유형별 상한을 한곳에 모은다.
 * 두 경로가 이 추정을 공유하므로 시설 개수가 엔드포인트마다 어긋나지 않는다(계약 정합).
 *
 * 실 공간데이터가 있는 유형(CCTV/비상벨 등 회랑 질의)은 이 추정 대신 실 데이터를 우선하고,
 * 실 데이터가 없는 유형(편의점·데이터 공백 구간)만 이 추정으로 채운다 — 배치·우선순위는 호출부 책임.
 */
object FacilityCountEstimator {

    /** 유형별 시설 개수 상한(응답 크기·지도 가독성 방어). */
    const val MAX_PER_TYPE = 8

    /** 출발→도착 보행거리(직선×우회계수)와 경로 유형 밀도로 유형별 시설 개수를 추정한다. */
    fun estimate(origin: GeoPoint, destination: GeoPoint, type: RouteType): FacilityCounts {
        val km = SpatialMath.haversineMeters(origin, destination) * type.distanceFactor / 1000.0
        return FacilityCounts(
            cctv = count(type, type.cctvPerKm, floorWhenSafe = 2, km = km),
            bell = count(type, type.bellPerKm, floorWhenSafe = 1, km = km),
            store = count(type, type.storePerKm, floorWhenSafe = 1, km = km),
            police = count(type, type.policePerKm, floorWhenSafe = 0, km = km),
            // 안심집(B-2)은 지정 거점이라 희소 — safe 경로에서만 최소 1개 보장.
            safehouse = count(type, type.safehousePerKm, floorWhenSafe = 1, km = km),
        )
    }

    /** km·밀도 → 개수(짧은 경로도 핵심 시설 1개는 보이도록 추천 경로엔 최소 보정). 상한으로 클램프. */
    private fun count(type: RouteType, perKm: Double, floorWhenSafe: Int, km: Double): Int {
        val raw = (km * perKm).roundToInt()
        val withFloor = if (type == RouteType.SAFE) maxOf(raw, floorWhenSafe) else raw
        return min(withFloor, MAX_PER_TYPE)
    }
}

/** 경로상 안심시설 유형별 추정 개수(cctv/bell/store/police/safehouse). */
data class FacilityCounts(
    val cctv: Int,
    val bell: Int,
    val store: Int,
    val police: Int,
    val safehouse: Int,
) {
    /** 마커 유형 문자열(cctv/bell/store/police/safehouse)에 대응하는 개수. 미지원 유형은 0. */
    fun forMarkerType(markerType: String): Int = when (markerType) {
        "cctv" -> cctv
        "bell" -> bell
        "store" -> store
        "police" -> police
        "safehouse" -> safehouse
        else -> 0
    }
}
