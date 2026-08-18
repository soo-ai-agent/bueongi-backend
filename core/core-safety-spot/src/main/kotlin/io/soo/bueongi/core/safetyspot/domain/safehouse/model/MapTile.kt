package io.soo.bueongi.core.safetyspot.domain.safehouse.model

import io.soo.bueongi.client.safe182.MapBounds
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import kotlin.math.floor

/**
 * 지도를 0.1도(약 11km) 격자로 자른 한 칸.
 *
 * 아동안전지킴이집 원천은 지역 코드를 안 받고 사각 범위만 받는다. 요청 범위를 그때그때
 * 만들면 캐시 열쇠가 매번 달라져 같은 데이터를 반복해 받게 된다. 칸으로 끊으면 열쇠가
 * 고정돼 한 번 받은 칸은 다시 받지 않는다(실측: 한 칸 216건 = 3회 호출).
 */
data class MapTile(val latCell: Int, val lngCell: Int) {

    /** 지역 캐시의 지역 코드 자리에 쓰는 값. */
    val code: String
        get() = "${latCell}_$lngCell"

    fun toBounds(): MapBounds =
        MapBounds(
            minLat = latCell * SIZE_DEGREES,
            minLng = lngCell * SIZE_DEGREES,
            maxLat = (latCell + 1) * SIZE_DEGREES,
            maxLng = (lngCell + 1) * SIZE_DEGREES,
        )

    companion object {
        const val SIZE_DEGREES: Double = 0.1

        fun of(point: GeoPoint): MapTile =
            MapTile(
                latCell = floor(point.lat / SIZE_DEGREES).toInt(),
                lngCell = floor(point.lng / SIZE_DEGREES).toInt(),
            )
    }
}
