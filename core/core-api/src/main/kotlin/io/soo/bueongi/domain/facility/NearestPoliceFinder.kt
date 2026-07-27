package io.soo.bueongi.domain.facility

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.SpatialMath
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * 최근접 지구대·파출소 탐색(구현 레이어 — 재사용 단위). 위급 기능 '주변 파출소 찾기'의 유스케이스로,
 * 반경 보정 → 공간 조회 → 거리 계산·정렬 → 상위 N 절단을 담당한다.
 * 컨트롤러(FacilityController)는 요청/응답 변환만 하고 이 파인더에 위임한다(규칙: Controller 얇게).
 */
@Component
class NearestPoliceFinder(
    private val repository: FacilitySpatialSource,
) {

    /** 중심 좌표 기준 최근접 파출소를 거리 오름차순으로 찾는다. 반경/개수는 안전 상한으로 보정한다. */
    fun findNearest(center: GeoPoint, radiusM: Double?, limit: Int?): List<NearestPolice> {
        val radius = (radiusM ?: DEFAULT_RADIUS_M).coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
        val maxResults = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return repository.index(FacilityType.POLICE)
            .withinRadius(center, radius)
            .map { point -> FacilityWithDistance(point, SpatialMath.haversineMeters(center, point.toGeoPoint())) }
            .sortedBy { it.distanceMeters }
            .take(maxResults)
            .map { found ->
                NearestPolice(
                    name = found.point.name,
                    address = found.point.address,
                    tel = found.point.phone,
                    lat = found.point.lat,
                    lng = found.point.lng,
                    distanceM = found.distanceMeters.roundToInt(),
                )
            }
    }

    companion object {
        const val DEFAULT_RADIUS_M = 10_000.0
        const val MIN_RADIUS_M = 100.0
        const val MAX_RADIUS_M = 20_000.0
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 10
    }
}

/**
 * 최근접 파출소 1건 — 도메인 결과이자 프론트 응답 계약(거리 오름차순 정렬). 동일 필드의 응답 DTO 를
 * 한 벌 더 두는 이중 정의 대신 단일 출처로 유지한다(RouteOption·ShareResults 와 같은 결정).
 * 프론트 NearbyPolice 계약과 동형이라 필드명(tel·distance_m — phone 아님)은 리네임 금지.
 */
data class NearestPolice(
    val name: String?,
    val address: String?,
    val tel: String?,
    val lat: Double,
    val lng: Double,
    @JsonProperty("distance_m")
    val distanceM: Int,
)

/** `GET /api/facilities/police/nearest` 응답 봉투 — 프론트 NearbyPolice 계약(거리순 정렬)과 동형. */
data class NearestPoliceResponse(val items: List<NearestPolice>)

/** 중심에서의 거리(m)를 붙인 시설 점 — 거리순 정렬·상위 N 절단의 중간 표현. */
private data class FacilityWithDistance(val point: FacilityPoint, val distanceMeters: Double)
