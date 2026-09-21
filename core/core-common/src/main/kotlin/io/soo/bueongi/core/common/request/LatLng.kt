package io.soo.bueongi.core.common.request

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull

// Jackson 이 빠진 숫자 칸에 0.0 을 채워 적도 좌표를 만들므로 nullable + @NotNull 이다
data class LatLng(
    @field:NotNull
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val lng: Double?,
)
