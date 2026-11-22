package io.soo.bueongi.core.safetyspot.api.controller

import io.soo.bueongi.core.safetyspot.api.request.NearbySafetySpotsRequest
import io.soo.bueongi.core.safetyspot.api.response.NearbySafetySpotsResponse
import io.soo.bueongi.core.safetyspot.domain.nearby.service.NearbySafetySpotService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/safety-spots")
class SafetySpotController(
    private val nearbySafetySpotService: NearbySafetySpotService,
) {
    @GetMapping("/nearby")
    fun findNearby(request: NearbySafetySpotsRequest): NearbySafetySpotsResponse {
        return nearbySafetySpotService.count(request.center)
    }
}
