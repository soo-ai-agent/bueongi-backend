package io.soo.bueongi.core.admin.safetyspot.domain.status.model

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint

data class RegionSafetySpotCount(
    val code: String,
    val sido: String,
    val name: String,
    val countsByType: Map<SafetySpotType, Int>,
    /** 파일(CDN·업로드) 출처의 수. */
    val fileCount: Int,
    /** 외부 API 지역 수집 출처의 수. */
    val apiCount: Int,
    /** 지역 미상이거나 국내 좌표가 하나도 없으면 null — 지도에 중심을 찍지 않는다. */
    val center: GeoPoint?,
) {
    val total: Int
        get() {
            return fileCount + apiCount
        }
}
