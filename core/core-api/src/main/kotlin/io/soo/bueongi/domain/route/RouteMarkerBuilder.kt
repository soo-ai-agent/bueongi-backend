package io.soo.bueongi.domain.route

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.domain.facility.SafetyScorer
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * 경로변 거점 마커 생성 구현체 — 출발/도착 + 회랑 내 시설(CCTV/안심집/비상벨/지구대·파출소/가로등)을
 * [FacilityPoi] 로 만든다. 저장소 회랑 질의와 x/y 정규화 투영이라는 세부 계산을 이 구현체에 격리해
 * [SafeRouteService] 는 순서 지휘만 남긴다(Service=지휘자 규칙). 산출물([FacilityPoi])이 route 응답
 * 계약이라 facility 가 아닌 route 도메인에 둔다(역방향 참조 방지).
 *
 * 실지도용 lat/lng 는 그대로 두고, MapMock 폴백용 정규화 x/y(0~100, 여백 12%)는 전 마커
 * 경계상자에 투영한다(프론트 buildRouteMarkers 동형 — 같은 입력/데이터 → 같은 결과).
 *
 * 편의점(store)처럼 실 공간데이터가 없는 유형과, 회랑 안에 실 시설이 하나도 없는 유형(예: 좁은 30m
 * 회랑을 벗어난 지구대·안심집)은 프론트 시설 그리드에서 0으로만 보인다. 이를 메우기 위해 그리드가 세는
 * 유형(store/police/safehouse) 중 회랑 실측이 0인 것만 [FacilityCountEstimator] 추정 개수만큼 실제 경로
 * 위에 표시한다 — `/api/routes/facilities` 와 같은 개수 근거를 공유해 두 화면의 시설 집합을 정렬한다.
 */
@Component
class RouteMarkerBuilder(
    private val repository: FacilitySpatialSource,
) {

    fun buildMarkers(
        origin: GeoPoint,
        destination: GeoPoint,
        path: List<GeoPoint>,
        routeType: RouteType,
    ): List<FacilityPoi> {
        val corridor = SafetyScorer.DEFAULT_CORRIDOR_METERS
        val raw = ArrayList<FacilityPoi>()
        raw.add(FacilityPoi(type = "start", x = 0, y = 0, lat = origin.lat, lng = origin.lng))
        raw.add(FacilityPoi(type = "end", x = 0, y = 0, lat = destination.lat, lng = destination.lng))

        // 회랑 내 실 시설(실좌표·메타 포함). 유형별로 몇 개가 실측됐는지 기록해 아래 추정 보완 대상을 가린다.
        val realCountByType = HashMap<String, Int>()
        for ((markerType, facilityType) in MARKER_TYPES) {
            // 유형별 마커 수 상한 — 가로등처럼 밀집한 시설이 지도를 가리지 않도록 방어(점수 계산은 전체 개수를 그대로 쓴다).
            val facilities = repository.index(facilityType).withinCorridor(path, corridor).take(MAX_MARKERS_PER_TYPE)
            realCountByType[markerType] = facilities.size
            for (facility in facilities) {
                raw.add(
                    FacilityPoi(
                        type = markerType,
                        x = 0,
                        y = 0,
                        lat = facility.lat,
                        lng = facility.lng,
                        name = facility.name,
                        purpose = facility.purpose,
                        cameraCount = facility.cameraCount,
                        address = facility.address,
                        phone = facility.phone,
                    ),
                )
            }
        }

        // 그리드가 세는 유형(store/police/safehouse) 중 회랑 실측이 0인 것만 추정 개수만큼 경로 위에 채운다.
        // store 는 실 공간데이터 자체가 없어 항상 추정, police/safehouse 는 좁은 회랑을 벗어난 경우 추정으로 보완.
        val counts = FacilityCountEstimator.estimate(origin, destination, routeType)
        for (markerType in ESTIMATED_FILL_TYPES) {
            if ((realCountByType[markerType] ?: 0) > 0) continue
            val needed = counts.forMarkerType(markerType)
            for (i in 0 until needed) {
                // 경로(폴리라인)를 따라 균등 분포 — 실제 귀갓길 위에 놓아 회랑 밖 이질감이 없게 한다(결정론적).
                val point = pointAlongPath(path, (i + 1).toDouble() / (needed + 1))
                raw.add(FacilityPoi(type = markerType, x = 0, y = 0, lat = point.lat, lng = point.lng))
            }
        }

        val minLat = raw.minOf { it.lat }
        val maxLat = raw.maxOf { it.lat }
        val minLng = raw.minOf { it.lng }
        val maxLng = raw.maxOf { it.lng }
        val range = 100.0 - 2 * MARKER_PADDING
        val spanLat = maxLat - minLat
        val spanLng = maxLng - minLng
        return raw.map { marker ->
            // 한 축이 퇴화(모든 좌표 동일)하면 가운데(50). 위도는 위쪽이 큰 값이라 y축을 뒤집는다.
            val x = if (spanLng == 0.0) {
                50
            } else {
                (MARKER_PADDING + (marker.lng - minLng) / spanLng * range).roundToInt()
            }
            val y = if (spanLat == 0.0) {
                50
            } else {
                (MARKER_PADDING + (maxLat - marker.lat) / spanLat * range).roundToInt()
            }
            marker.copy(x = x.coerceIn(0, 100), y = y.coerceIn(0, 100))
        }
    }

    /** 경로(폴리라인)의 누적 길이 기준 t(0~1) 지점 좌표. 빈/단일·길이 0 경로는 첫 점으로 방어. */
    private fun pointAlongPath(path: List<GeoPoint>, t: Double): GeoPoint {
        if (path.size < 2) return path.first()
        val total = SpatialMath.pathLengthMeters(path)
        if (total == 0.0) return path.first()
        val target = total * t
        var acc = 0.0
        for (i in 1 until path.size) {
            val a = path[i - 1]
            val b = path[i]
            val seg = SpatialMath.haversineMeters(a, b)
            if (acc + seg >= target) {
                val f = if (seg == 0.0) 0.0 else (target - acc) / seg
                return GeoPoint(a.lat + (b.lat - a.lat) * f, a.lng + (b.lng - a.lng) * f)
            }
            acc += seg
        }
        return path.last()
    }

    private companion object {
        // 마커 표시 종류 → 시설 인덱스(회랑 실측; 실좌표·메타 포함).
        val MARKER_TYPES = listOf(
            "cctv" to FacilityType.CCTV,
            "safehouse" to FacilityType.SAFEHOUSE,
            "bell" to FacilityType.BELL,
            // 지구대·파출소도 경로변 거점 마커로 표시한다(여성안심지킴이집과 함께 지도에 노출).
            "police" to FacilityType.POLICE,
            // 가로등·조명은 점수(밀도)에도 쓰이고, 지도에도 표시한다(상한으로 밀집 방어).
            "lamp" to FacilityType.LAMP,
        )

        /**
         * 프론트 시설 그리드(summarizeRouteMarkers)가 세는 유형 중, 회랑 실측이 0일 때 추정으로 보완할 유형.
         * cctv/bell 은 실 데이터가 풍부해 보완 대상이 아니고, store 는 실 데이터가 없어 항상 추정된다.
         */
        val ESTIMATED_FILL_TYPES = listOf("store", "police", "safehouse")

        /** 정규화 x/y 투영의 경계 여백(%). */
        const val MARKER_PADDING = 12.0

        /** 유형별 마커 수 상한(가로등 등 밀집 시설로 인한 지도 가독성 저하·응답 비대화 방어). */
        const val MAX_MARKERS_PER_TYPE = 12
    }
}
