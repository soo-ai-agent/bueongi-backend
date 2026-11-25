package io.soo.bueongi.core.admin.safetyspot.domain.status.model

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint

data class RegionSafetySpotCount(
    val code: String,
    val sido: String,
    val name: String,
    val countsByType: Map<SafetySpotType, Int>,
    val fileCount: Int,
    val apiCount: Int,
    /** 지역 미상이거나 국내 좌표가 하나도 없으면 null — 지도에 중심을 찍지 않는다. */
    val center: GeoPoint?,
) {
    val total: Int
        get() {
            return fileCount + apiCount
        }
}
