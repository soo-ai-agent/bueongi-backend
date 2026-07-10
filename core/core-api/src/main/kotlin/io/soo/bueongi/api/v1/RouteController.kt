package io.soo.bueongi.api.v1

import io.soo.bueongi.api.v1.request.RouteRequest
import io.soo.bueongi.api.v1.request.toValidatedQuery
import io.soo.bueongi.domain.route.FacilitiesResponse
import io.soo.bueongi.domain.route.RouteFacilityService
import io.soo.bueongi.domain.route.RouteOption
import io.soo.bueongi.domain.route.RouteService
import io.soo.bueongi.domain.route.SafeRouteService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 경로 비교 + 안심시설 API. 요청 검증·도메인 질의 변환([RouteRequest.toValidatedQuery])까지만 담당하고
 * 흐름은 도메인 서비스에 위임한다. 모두 출발/도착 좌표를 받아 상태 없이(stateless) 응답한다.
 */
@RestController
@RequestMapping("/api/routes")
class RouteController(
    private val routeService: RouteService,
    private val routeFacilityService: RouteFacilityService,
    private val safeRouteService: SafeRouteService,
) {

    /** POST /api/routes/compare → safe/main/fast 경로 목록(프론트 MockRoute 와 동일 필드). */
    @PostMapping("/compare")
    fun compare(@Valid @RequestBody request: RouteRequest): List<RouteOption> =
        routeService.compare(request.toValidatedQuery())

    /**
     * POST /api/routes/safe-compare → 실 공공데이터 기반 안심 경로 목록.
     * Tmap 키가 있으면 실 보행자 경로 + 안심 점수/우회/마커를 채운 RouteOption, 없으면 compare 폴백.
     * 입력은 compare 와 동일하되 `safetyPreference`(balanced|safe|safest)로 우회 강도를 조절한다.
     */
    @PostMapping("/safe-compare")
    fun safeCompare(@Valid @RequestBody request: RouteRequest): List<RouteOption> =
        safeRouteService.safeCompare(request.toValidatedQuery())

    /**
     * POST /api/routes/facilities → 선택 경로(routeType)상의 안심시설 POI + 집계.
     * routeId 가 아닌 출발/도착 좌표 기반(stateless) — 새로고침·직접진입에도 끊기지 않는다.
     */
    @PostMapping("/facilities")
    fun facilities(@Valid @RequestBody request: RouteRequest): FacilitiesResponse =
        routeFacilityService.alongRoute(request.toValidatedQuery())
}
