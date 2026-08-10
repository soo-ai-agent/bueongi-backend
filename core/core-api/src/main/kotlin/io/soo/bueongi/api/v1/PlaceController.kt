package io.soo.bueongi.api.v1
import io.soo.bueongi.domain.place.PlaceItem
import io.soo.bueongi.domain.place.PlaceSearchService

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/places")
class PlaceController(
    private val placeSearchService: PlaceSearchService,
) {

    @GetMapping("/search")
    fun search(@RequestParam(required = false) keyword: String?): List<PlaceItem> =
        placeSearchService.search(keyword)
}
