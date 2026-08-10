package io.soo.bueongi.api.ads

import io.soo.bueongi.api.ads.request.AdImpressionRequest
import io.soo.bueongi.api.ads.request.placementValue
import io.soo.bueongi.domain.admin.AdStatsService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ads")
class AdController(
    private val adStatsService: AdStatsService,
) {

    @PostMapping("/impression")
    fun impression(@Valid @RequestBody request: AdImpressionRequest): ResponseEntity<Void> {
        adStatsService.record(request.placementValue())
        return ResponseEntity.noContent().build()
    }
}
