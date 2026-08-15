package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.core.place.domain.model.PlaceItem
import io.soo.bueongi.core.place.domain.model.SEED_PLACE_CATALOG
import org.springframework.stereotype.Component

@Component
class SeedPlaceProvider : PlaceProvider {
    override fun search(keyword: String): List<PlaceItem> {
        val needle: String = keyword.lowercase()
        return SEED_PLACE_CATALOG.filter { place ->
            place.name.lowercase().contains(needle) || place.address.lowercase().contains(needle)
        }
    }
}
