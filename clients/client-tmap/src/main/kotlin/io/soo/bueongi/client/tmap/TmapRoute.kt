package io.soo.bueongi.client.tmap

import kotlin.math.roundToLong

data class TmapRoute(
    val searchOption: String,
    val path: List<TmapCoord>,
    val distanceM: Double,
    val timeS: Int,
    val steps: List<NavStep>,
) {
    /** 중복 제거 키 — 좌표수가 PATH_SIZE_SLOT(1000) 미만 전제, 초과 시 키 충돌. */
    val identityKey: Long
        get() = (distanceM.roundToLong() * PATH_SIZE_SLOT) + path.size

    companion object {
        const val PATH_SIZE_SLOT: Long = 1_000L
    }
}
