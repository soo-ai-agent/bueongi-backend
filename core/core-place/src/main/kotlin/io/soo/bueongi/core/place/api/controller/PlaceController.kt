package io.soo.bueongi.core.place.api.controller

import io.soo.bueongi.core.place.api.request.PlaceSearchRequest
import io.soo.bueongi.core.place.api.response.PlaceResponse
import io.soo.bueongi.core.place.domain.service.PlaceSearchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/places")
class PlaceController(private val placeSearchService: PlaceSearchService) {
    @GetMapping("/search")
    fun search(request: PlaceSearchRequest): List<PlaceResponse> {
        return placeSearchService.search(request.searchKeyword)
    }
}
