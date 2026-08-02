package io.soo.bueongi.domain.route

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.domain.facility.SafetyScorer
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * 경로변 거점 마커 생성 구현체 — 출발/도착 + 회랑 내 **실측 시설**(CCTV/안심집/비상벨/지구대·파출소/가로등)을
 * [FacilityPoi] 로 만든다. 저장소 회랑 질의와 x/y 정규화 투영이라는 세부 계산을 이 구현체에 격리해
 * [SafeRouteService] 는 순서 지휘만 남긴다(Service=지휘자 규칙). 산출물([FacilityPoi])이 route 응답
 * 계약이라 facility 가 아닌 route 도메인에 둔다(역방향 참조 방지).
 *
 * 실지도용 lat/lng 는 그대로 두고, MapMock 폴백용 정규화 x/y(0~100, 여백 12%)는 전 마커
 * 경계상자에 투영한다(프론트 buildRouteMarkers 동형 — 같은 입력/데이터 → 같은 결과).
 *
 * 정직 원칙(밀도 추정 보완 제거): 회랑 30m 안의 **실측 시설만** 마커로 낸다. 회랑에 실 시설이 하나도 없는
 * 유형은 그냥 0(마커 없음)으로 남는다 — 없는 개수를 지어내지 않는다. 프론트 시설 그리드는 0 유형 칩을
 * 숨기므로 화면엔 실제로 경로변에 있는 시설만 보인다.
 *
 * 편의점(store)은 [MARKER_TYPES]에 없다 — 사용자 결정으로 **지도에서 제외**한다(실데이터화 안 함). 따라서
 * 경로 마커·시설 그리드·점수 어디에도 편의점은 나타나지 않는다(응답 계약상 store 요약 필드는 항상 0 유지).
 */
@Component
class RouteMarkerBuilder(
    private val repository: FacilitySpatialSource,
    /**
     * 운영 알림(없으면 null 무해) — 시설 인덱스가 **통째로 비어(총 적재 0)** 마커를 구조적으로 만들 수 없을 때만 쓴다.
     * "회랑에 실제 시설이 없어 정상적으로 0"인 경우(적재량>0)에는 알리지 않는다([alertIfIndexEmpty] 참고).
     */
    private val alertNotifier: AlertNotifier? = null,
) {

    fun buildMarkers(
        origin: GeoPoint,
        destination: GeoPoint,
        path: List<GeoPoint>,
    ): List<FacilityPoi> {
        alertIfIndexEmpty()

        val corridor: Double = SafetyScorer.DEFAULT_CORRIDOR_METERS
        val raw: ArrayList<FacilityPoi> = ArrayList<FacilityPoi>()
        raw.add(FacilityPoi(type = FacilityPoiType.START, x = 0, y = 0, lat = origin.lat, lng = origin.lng))
        raw.add(FacilityPoi(type = FacilityPoiType.END, x = 0, y = 0, lat = destination.lat, lng = destination.lng))

        for ((markerType, facilityType) in MARKER_TYPES) {
            val facilities: List<FacilityPoint> =
                repository.index(facilityType).withinCorridor(path, corridor).take(MAX_MARKERS_PER_TYPE)
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

        val minLat: Double = raw.minOf { it.lat }
        val maxLat: Double = raw.maxOf { it.lat }
        val minLng: Double = raw.minOf { it.lng }
        val maxLng: Double = raw.maxOf { it.lng }
        val range: Double = 100.0 - 2 * MARKER_PADDING
        val spanLat: Double = maxLat - minLat
        val spanLng: Double = maxLng - minLng
        return raw.map { marker ->
            val x: Int = if (spanLng == 0.0) {
                50
            } else {
                (MARKER_PADDING + (marker.lng - minLng) / spanLng * range).roundToInt()
            }
            val y: Int = if (spanLat == 0.0) {
                50
            } else {
                (MARKER_PADDING + (maxLat - marker.lat) / spanLat * range).roundToInt()
            }
            marker.copy(x = x.coerceIn(0, 100), y = y.coerceIn(0, 100))
        }
    }

    /**
     * 시설 데이터 제공 실패 감지 — 마커 대상 유형의 **총 적재량이 0**이면 인덱스 자체가 비어 있다(파일/원천 적재 실패
     * 또는 데이터 부재). 이 경우 어떤 회랑을 질의해도 구조적으로 0이라 운영 이상으로 알린다. 반대로 적재량>0인데
     * 특정 회랑만 0인 것은 "실제로 그 경로변에 시설이 없는" **정직한 0**이므로 알리지 않는다.
     *
     * 즉 판정은 (총 적재량 0) 대 (특정 회랑 0)의 구분이다 — 전자만 장애다. 알림은 부수효과라 반환 마커에 영향 없다.
     * key 를 상수로 고정해 요청마다의 알림 폭주를 [AlertNotifier] 스로틀(기본 10분)로 억제한다.
     */
    private fun alertIfIndexEmpty() {
        val notifier: AlertNotifier = alertNotifier ?: return
        val totalLoaded: Int = MARKER_TYPES.sumOf { (_, facilityType) -> repository.index(facilityType).size }
        if (totalLoaded > 0) {
            return
        }
        notifier.notify(
            key = "facility-index-empty",
            title = "안심시설 데이터 제공 실패",
            detail = "시설 인덱스 총 적재 0(적재 실패/부재) — /api/routes/facilities·/api/routes/safe-compare 의 " +
                "경로변 시설 마커를 생성할 수 없음. 데이터 적재/원천 확인 필요",
        )
    }

    private companion object {
        val MARKER_TYPES: List<Pair<FacilityPoiType, FacilityType>> = listOf(
            FacilityPoiType.CCTV to FacilityType.CCTV,
            FacilityPoiType.SAFEHOUSE to FacilityType.SAFEHOUSE,
            FacilityPoiType.BELL to FacilityType.BELL,
            FacilityPoiType.POLICE to FacilityType.POLICE,
            FacilityPoiType.LAMP to FacilityType.LAMP,
        )

        /** 정규화 x/y 투영의 경계 여백(%). */
        const val MARKER_PADDING: Double = 12.0

        /** 유형별 마커 수 상한(가로등 등 밀집 시설로 인한 지도 가독성 저하·응답 비대화 방어). */
        const val MAX_MARKERS_PER_TYPE: Int = 12
    }
}
