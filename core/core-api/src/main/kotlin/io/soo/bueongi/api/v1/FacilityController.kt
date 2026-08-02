package io.soo.bueongi.api.v1

import io.soo.bueongi.domain.facility.NearestPoliceFinder
import io.soo.bueongi.domain.facility.NearestPoliceResponse
import io.soo.bueongi.api.v1.request.toValidatedGeoPoint
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 위급 기능(주변 파출소 찾기) 최근접 조회 API — 요청 검증/응답 변환만 담당하고
 * 탐색 흐름은 [NearestPoliceFinder] 에 위임한다.
 *
 * 프론트는 로컬 캐시 → CDN 순으로 파출소 데이터를 찾고, 둘 다 없을 때 이 API로 폴백한다 —
 * 위급 기능이 클라이언트 데이터 배포(CDN 설정) 하나에 매달리지 않도록 백엔드가 적재한
 * 공공데이터를 같은 계약(거리순·반경 제한)으로 제공한다.
 */
@RestController
@RequestMapping("/api/facilities")
class FacilityController(
    private val nearestPoliceFinder: NearestPoliceFinder,
) {

    @GetMapping("/police/nearest")
    fun nearestPolice(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(name = "radius_m", required = false) radiusM: Double?,
        @RequestParam(required = false) limit: Int?,
    ): NearestPoliceResponse {
        val origin: GeoPoint = toValidatedGeoPoint(lat, lng)
        return NearestPoliceResponse(nearestPoliceFinder.findNearest(origin, radiusM, limit))
    }
}
