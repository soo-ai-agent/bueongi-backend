package io.soo.bueongi.core.safetyspot.domain.safehouse.model

import io.soo.bueongi.client.safe182.MapBounds
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import kotlin.math.floor

// 아동안전지킴이집 원천은 지역 코드를 안 받고 사각 범위만 받아, 캐시 열쇠를 격자 칸으로 고정한다.
data class MapTile(
    val latCell: Int,
    val lngCell: Int,
) {
    val code: String
        get() = "${latCell}_$lngCell"

    fun toBounds(): MapBounds {
        return MapBounds(
            minLat = latCell * SIZE_DEGREES,
            minLng = lngCell * SIZE_DEGREES,
            maxLat = (latCell + 1) * SIZE_DEGREES,
            maxLng = (lngCell + 1) * SIZE_DEGREES,
        )
    }

    companion object {
        const val SIZE_DEGREES: Double = 0.1

        fun of(point: GeoPoint): MapTile {
            return MapTile(
                latCell = floor(point.lat / SIZE_DEGREES).toInt(),
                lngCell = floor(point.lng / SIZE_DEGREES).toInt(),
            )
        }
    }
}
