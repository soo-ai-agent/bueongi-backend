package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.core.place.domain.model.PlaceItem
import io.soo.bueongi.core.place.domain.model.SEED_PLACE_CATALOG
import org.springframework.stereotype.Component

@Component
class SeedPlaceProvider {
    fun search(keyword: String): List<PlaceItem> {
        val needle: String = keyword.lowercase()
        val found: ArrayList<PlaceItem> = ArrayList()
        for (place in SEED_PLACE_CATALOG) {
            val inName: Boolean = place.name.lowercase().contains(needle)
            val inAddress: Boolean = place.address.lowercase().contains(needle)
            if (inName || inAddress) {
                found.add(place)
            }
        }
        return found
    }
}
