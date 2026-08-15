package io.soo.bueongi.core.safetyspot.api.controller

import io.soo.bueongi.core.safetyspot.domain.service.NearestPoliceFinder
import io.soo.bueongi.core.safetyspot.api.response.NearestPoliceResponse
import io.soo.bueongi.core.safetyspot.api.request.toValidatedGeoPoint

import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/safety-spots")
class SafetySpotController(
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
