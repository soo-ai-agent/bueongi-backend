package io.soo.bueongi.core.admin.safetyspot.domain.status.model

import io.soo.bueongi.core.enums.SafetySpotType

// lastRefresh* 는 그 종류의 갱신 기록이 아직 없으면 null 이다.
data class SafetySpotStatus(
    val type: SafetySpotType,
    /** 파일(CDN·업로드) 출처의 수. */
    val fileCount: Int,
    /** 외부 API 지역 수집 출처의 수. */
    val apiCount: Int,
    val lastRefreshAtMs: Long?,
    val lastRefreshCount: Int?,
) {
    val loadedCount: Int
        get() {
            return fileCount + apiCount
        }
}
