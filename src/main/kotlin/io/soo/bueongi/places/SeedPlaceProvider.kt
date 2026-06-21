package io.soo.bueongi.places

import org.springframework.stereotype.Component

/**
 * 내장 시드 카탈로그([SEED_PLACE_CATALOG]) 기반 검색. 외부 키가 없거나 실연동이 실패했을 때의 폴백 소스다.
 *
 * 프론트 `filterPlaces(catalog, keyword)` 와 동형: 이름/주소 어느 쪽이든 부분일치, 라틴 대소문자 무시.
 * (검색어 트림/빈값 가드는 [PlaceSearchService] 가 처리하므로 여기서는 비어있지 않은 검색어만 받는다.)
 */
@Component
class SeedPlaceProvider : PlaceProvider {
    override fun search(keyword: String): List<PlaceItem> {
        val needle = keyword.lowercase()
        return SEED_PLACE_CATALOG.filter { place ->
            place.name.lowercase().contains(needle) || place.address.lowercase().contains(needle)
        }
    }
}
