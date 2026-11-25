package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import org.springframework.stereotype.Component

// 좌표가 속한 자치단체(개방자치단체코드 7자리)를 가장 가까운 CCTV 로 찾는다 — API 채우기와 업로드가 같은 기준을 쓴다.
// CCTV 는 전국에 고르게 많고 원천이 개방자치단체코드를 준다. 20km 안에 없으면 찾지 못한 것으로 본다.
@Component
class RegionLocator(
    private val safetySpots: SafetySpotSpatialSource,
) {
    fun locate(point: GeoPoint): String? {
        val nearest: SafetySpotPoint = safetySpots.findNearestWithRegion(SafetySpotType.CCTV, point, MAX_SEARCH_METERS) ?: return null
        return nearest.regionCodeOrNull()
    }

    private companion object {
        const val MAX_SEARCH_METERS: Double = 20_000.0
    }
}
