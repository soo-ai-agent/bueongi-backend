package io.soo.bueongi.core.common.request

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull

/** 빠진 값도 받아야 어느 필드가 틀렸는지 응답에 담긴다. */
data class LatLng(
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
)
