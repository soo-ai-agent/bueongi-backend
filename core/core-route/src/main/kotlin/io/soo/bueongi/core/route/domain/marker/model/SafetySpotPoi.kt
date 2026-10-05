package io.soo.bueongi.core.route.domain.marker.model

import io.soo.bueongi.storage.db.core.safetyspot.model.CctvSpot
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.phoneOrNull
import kotlin.math.roundToInt

// x·y 는 위경도가 아니라 프론트 미니맵용 0~100 백분율 좌표다(프론트 계약).
data class SafetySpotPoi(
    val type: SafetySpotPoiType,
    val x: Int,
    val y: Int,
    val lat: Double,
    val lng: Double,
    val name: String = "",
    val purpose: String = "",
    val cameraCount: Int? = null,
    val address: String = "",
    val phone: String = "",
)

// 설치 목적·대수는 CCTV 만 갖는다.
internal fun SafetySpotPoint.toPoi(markerType: SafetySpotPoiType): SafetySpotPoi =
    SafetySpotPoi(
        type = markerType,
        x = 0,
        y = 0,
        lat = lat,
        lng = lng,
        name = name.orEmpty(),
        purpose = if (this is CctvSpot) purpose.orEmpty() else "",
        cameraCount = if (this is CctvSpot) cameraCount else null,
        address = address.orEmpty(),
        phone = phoneOrNull().orEmpty(),
    )

// 백분율은 목록 전체의 위경도 범위가 있어야 나오므로 마커 하나가 아니라 목록이 계산한다.
internal fun List<SafetySpotPoi>.toMiniMapPercent(): List<SafetySpotPoi> {
    val minLat: Double = minOf { it.lat }
    val maxLat: Double = maxOf { it.lat }
    val minLng: Double = minOf { it.lng }
    val maxLng: Double = maxOf { it.lng }
    val spanLat: Double = maxLat - minLat
    val spanLng: Double = maxLng - minLng
    val drawableRange: Double = MINI_MAP_MAX - 2 * MARKER_PADDING
    val placed: ArrayList<SafetySpotPoi> = ArrayList()
    for (marker in this) {
        val x: Int = percentOrCenter(marker.lng - minLng, spanLng, drawableRange)
        val northFlippedOffset: Double = maxLat - marker.lat
        val y: Int = percentOrCenter(northFlippedOffset, spanLat, drawableRange)
        placed.add(marker.copy(x = x, y = y))
    }
    return placed
}

private fun percentOrCenter(offset: Double, span: Double, drawableRange: Double): Int {
    if (span == 0.0) {
        return MINI_MAP_CENTER
    }
    val ratio: Double = offset / span
    val percent: Double = MARKER_PADDING + ratio * drawableRange
    return percent.roundToInt().coerceIn(0, MINI_MAP_MAX.toInt())
}

private const val MINI_MAP_MAX: Double = 100.0
private const val MINI_MAP_CENTER: Int = 50
private const val MARKER_PADDING: Double = 12.0
