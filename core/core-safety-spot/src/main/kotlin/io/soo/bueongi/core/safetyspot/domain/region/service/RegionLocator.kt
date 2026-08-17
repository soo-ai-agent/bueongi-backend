package io.soo.bueongi.core.safetyspot.domain.region.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Component

/**
 * 좌표가 어느 자치단체인지 알아낸다.
 *
 * 별도 경계 데이터를 두지 않고 **이미 적재된 CCTV 지점**을 쓴다 — CCTV 는 전국 228개 자치단체를
 * 좌표와 함께 덮고 있어(37만 지점), 가장 가까운 CCTV 의 자치단체가 곧 그 좌표의 자치단체다.
 * 경계선 바로 옆에서는 이웃 자치단체가 나올 수 있지만, 용도가 "이 근처 데이터를 받아 둘까"라
 * 그 정도 오차는 문제가 되지 않는다.
 */
@Component
class RegionLocator(
    private val safetySpots: SafetySpotSpatialSource,
) {
    fun regionCodeAt(point: GeoPoint): String? {
        for (radius in SEARCH_RADII_M) {
            val nearby: List<SafetySpotPoint> = safetySpots.index(SafetySpotType.CCTV).withinRadius(point, radius)
            val nearest: SafetySpotPoint? = nearby
                .filter { !it.sigungu.isNullOrBlank() }
                .minByOrNull { point.distanceMetersTo(it.toGeoPoint()) }
            if (nearest != null) {
                return nearest.sigungu
            }
        }
        return null
    }

    private companion object {
        /** 도심은 첫 반경에서 잡히고, 시골은 넓혀야 잡힌다. 넓힐수록 느려지므로 단계로 올린다. */
        val SEARCH_RADII_M: List<Double> = listOf(500.0, 3_000.0, 20_000.0)
    }
}
