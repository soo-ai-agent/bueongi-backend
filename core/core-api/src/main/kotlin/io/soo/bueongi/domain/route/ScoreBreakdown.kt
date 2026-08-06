package io.soo.bueongi.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.storage.db.core.facility.GeoPoint

/**
 * 안전 점수 근거. SafetyScorer breakdown 의 프론트 계약형 — 사용자가 "왜 이 점수인가"를
 * 확인할 수 있게 밀도(개/km)·개수·겹침 비율을 그대로 노출한다.
 */
data class ScoreBreakdown(
    val routeKm: Double,
    val cctvDensity: Double,
    val lampDensity: Double,
    val bellDensity: Double,
    val safehouseCount: Int,
    val policeCount: Int,
    /** 안심귀갓길 겹침 비율(0~1). */
    val safePathOverlap: Double,
)
