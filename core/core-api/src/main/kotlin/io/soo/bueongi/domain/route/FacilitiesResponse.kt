package io.soo.bueongi.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** POST /api/routes/facilities 응답: 지도 표시용 POI 목록 + 집계 요약. */
data class FacilitiesResponse(
    val pois: List<FacilityPoi>,
    val summary: FacilitySummary,
)
