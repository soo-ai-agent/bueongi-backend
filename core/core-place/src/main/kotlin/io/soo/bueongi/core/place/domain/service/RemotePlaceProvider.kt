package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.core.place.domain.model.PlaceItem

interface RemotePlaceProvider {
    val isEnabled: Boolean

    // 응답 와이어 라벨 — "live" 또는 "seed".
    val sourceLabel: String
        get() = if (isEnabled) {
            "live"
        } else {
            "seed"
        }

    fun search(keyword: String): List<PlaceItem>
}
