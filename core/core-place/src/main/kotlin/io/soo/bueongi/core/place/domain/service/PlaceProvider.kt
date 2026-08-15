package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.core.place.domain.model.PlaceItem

interface PlaceProvider {
    fun search(keyword: String): List<PlaceItem>
}
