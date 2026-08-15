package io.soo.bueongi.core.route.domain

import io.soo.bueongi.core.route.marker.FacilityPoi

/** 경로 주변 안심시설 조회 결과. 응답 DTO 변환은 컨트롤러가 한다. */
data class RouteFacilities(
    val pois: List<FacilityPoi>,
    val summary: FacilitySummary,
)
