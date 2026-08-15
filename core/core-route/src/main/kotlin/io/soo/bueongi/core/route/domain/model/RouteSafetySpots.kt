package io.soo.bueongi.core.route.domain.model

import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi

/** 경로 주변 안심시설 조회 결과. 응답 DTO 변환은 컨트롤러가 한다. */
data class RouteSafetySpots(
    val pois: List<SafetySpotPoi>,
    val summary: SafetySpotSummary,
)
