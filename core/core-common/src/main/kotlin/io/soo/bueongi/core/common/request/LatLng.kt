package io.soo.bueongi.core.common.request

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull

/**
 * 검증 대상을 non-null 로 두면 값이 빠졌을 때 Jackson 이 0.0 을 채워 넣는다 —
 * 적도 좌표로 계산돼 "거리가 너무 멉니다"라는 엉뚱한 오류가 나간다(2026-08-16 실측).
 */
data class LatLng(
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
)
