package io.soo.bueongi.routes

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 경로 비교 + 안심시설 API. 프론트 RouteComparison/RouteDetail 화면이 mock(mockRoutes/detailPois) 대신
 * 호출하도록 동일 계약을 제공한다. 둘 다 출발/도착 좌표를 받아 상태 없이(stateless) 응답한다.
 */
@RestController
@RequestMapping("/api/routes")
class RouteController(
    private val routeService: RouteService,
    private val facilityService: FacilityService,
) {

    /** POST /api/routes/compare → safe/main/fast 경로 목록(프론트 MockRoute 와 동일 필드). */
    @PostMapping("/compare")
    fun compare(@Valid @RequestBody request: RouteRequest): List<RouteOption> =
        routeService.compare(request)

    /**
     * POST /api/routes/facilities → 선택 경로(routeType)상의 안심시설 POI + 집계.
     * routeId 가 아닌 출발/도착 좌표 기반(stateless) — 새로고침·직접진입에도 끊기지 않는다.
     */
    @PostMapping("/facilities")
    fun facilities(@Valid @RequestBody request: RouteRequest): FacilitiesResponse =
        facilityService.alongRoute(request)
}
