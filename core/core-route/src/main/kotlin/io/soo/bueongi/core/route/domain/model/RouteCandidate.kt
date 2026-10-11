package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import kotlin.math.roundToLong

data class RouteCandidate(
    val searchOption: String,
    val path: List<GeoPoint>,
    val distanceM: Double,
    val timeS: Int,
    val steps: List<NavigationStep>,
    // 기본(MAIN) 유형은 우회 후보를 집지 않아서, 안심시설 경유지를 강제한 우회 후보인지 표시해 둔다.
    val isDetour: Boolean = false,
) {
    // 좌표수가 PATH_SIZE_SLOT 미만이라는 전제다 — 넘으면 키가 충돌한다.
    val identityKey: Long
        get() = (distanceM.roundToLong() * PATH_SIZE_SLOT) + path.size

    companion object {
        const val PATH_SIZE_SLOT: Long = 1_000L
    }
}
