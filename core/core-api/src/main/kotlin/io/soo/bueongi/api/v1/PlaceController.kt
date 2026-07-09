package io.soo.bueongi.api.v1
import io.soo.bueongi.domain.place.PlaceItem
import io.soo.bueongi.domain.place.PlaceSearchService

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 장소 검색 API. 프론트 PlaceSearch 화면의 type-to-filter 가 mock 카탈로그 대신 호출하도록
 * 동일 의미(부분일치 검색)를 제공한다.
 */
@RestController
@RequestMapping("/api/places")
class PlaceController(
    private val placeSearchService: PlaceSearchService,
) {

    /** GET /api/places/search?keyword= → 부분일치 장소 목록(빈 검색어면 빈 목록). */
    @GetMapping("/search")
    fun search(@RequestParam(required = false) keyword: String?): List<PlaceItem> =
        placeSearchService.search(keyword)
}
