package io.soo.bueongi.facility

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.math.roundToInt

/**
 * 위급 기능(주변 파출소 찾기) 최근접 조회 API.
 *
 * 프론트는 로컬 캐시 → CDN 순으로 파출소 데이터를 찾고, 둘 다 없을 때 이 API로 폴백한다 —
 * 위급 기능이 클라이언트 데이터 배포(CDN 설정) 하나에 매달리지 않도록 백엔드가 적재한
 * 공공데이터를 같은 계약(거리순·반경 제한)으로 제공한다.
 */
@RestController
@RequestMapping("/api/facilities")
class NearestPoliceController(
    private val repository: FacilitySpatialSource,
) {

    @GetMapping("/police/nearest")
    fun nearest(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(name = "radius_m", required = false) radiusM: Double?,
        @RequestParam(required = false) limit: Int?,
    ): ResponseEntity<Any> {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "위경도 범위가 올바르지 않습니다."),
            )
        }
        val radius = (radiusM ?: DEFAULT_RADIUS_M).coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
        val max = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val center = GeoPoint(lat, lng)
        val items = repository.index(FacilityType.POLICE)
            .withinRadius(center, radius)
            .map { it to SpatialMath.haversineMeters(center, it.toGeoPoint()) }
            .sortedBy { (_, distance) -> distance }
            .take(max)
            .map { (point, distance) ->
                NearestPoliceItem(
                    name = point.name,
                    address = point.address,
                    tel = point.phone,
                    lat = point.lat,
                    lng = point.lng,
                    distanceM = distance.roundToInt(),
                )
            }
        return ResponseEntity.ok(NearestPoliceResponse(items))
    }

    companion object {
        const val DEFAULT_RADIUS_M = 10_000.0
        const val MIN_RADIUS_M = 100.0
        const val MAX_RADIUS_M = 20_000.0
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 10
    }
}

/** 최근접 파출소 응답 — 프론트 NearbyPolice 계약(거리순 정렬)과 동형. */
data class NearestPoliceResponse(val items: List<NearestPoliceItem>)

data class NearestPoliceItem(
    val name: String?,
    val address: String?,
    val tel: String?,
    val lat: Double,
    val lng: Double,
    @JsonProperty("distance_m")
    val distanceM: Int,
)
