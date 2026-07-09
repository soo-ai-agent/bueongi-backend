package io.soo.bueongi.domain.facility

import io.soo.bueongi.core.enums.FacilityType
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
            .map { point -> point to SpatialMath.haversineMeters(center, point.toGeoPoint()) }
            .sortedBy { (_, distance) -> distance }
            .take(maxResults)
            .map { (point, distance) ->
                NearestPolice(
                    name = point.name,
                    address = point.address,
                    tel = point.phone,
                    lat = point.lat,
                    lng = point.lng,
                    distanceM = distance.roundToInt(),
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

/** 최근접 파출소 1건(도메인 결과). 거리 오름차순 정렬로 반환된다. */
data class NearestPolice(
    val name: String?,
    val address: String?,
    val tel: String?,
    val lat: Double,
    val lng: Double,
    val distanceM: Int,
)
