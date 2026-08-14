package io.soo.bueongi.facility

import io.soo.bueongi.api.v1.request.toValidatedGeoPoint
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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
