package io.soo.bueongi.client.tmap

import kotlin.math.roundToLong

data class TmapRoute(
    val searchOption: String,
    val path: List<TmapCoord>,
    val distanceM: Double,
    val timeS: Int,
    val steps: List<NavStep>,
) {
    // 좌표수가 PATH_SIZE_SLOT 미만이라는 전제다 — 넘으면 키가 충돌한다.
    val identityKey: Long
        get() = (distanceM.roundToLong() * PATH_SIZE_SLOT) + path.size

    companion object {
        const val PATH_SIZE_SLOT: Long = 1_000L
    }
}
