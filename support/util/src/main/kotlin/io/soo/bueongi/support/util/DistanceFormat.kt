package io.soo.bueongi.support.util

import kotlin.math.max
import kotlin.math.roundToInt

object DistanceFormat {

    fun formatDistance(meters: Double): String {
        val clampedMeters: Double = max(0.0, meters)
        return if (clampedMeters < 1000) {
            "${(clampedMeters / 10).roundToInt() * 10}m"
        } else {
            "%.1fkm".format(clampedMeters / 1000.0)
        }
    }

    fun walkMinutes(meters: Double): Int =
        max(1, (max(0.0, meters) / 67.0).roundToInt())
}
