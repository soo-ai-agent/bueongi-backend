package io.soo.bueongi.core.common.request

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull

data class NamedLatLng(
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    // non-null 이면 안 보낸 값에 0.0 이 채워져 적도 좌표가 되므로 null 이 가능함
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    // non-null 이면 안 보낸 값에 0.0 이 채워져 적도 좌표가 되므로 null 이 가능함
    val lng: Double?,
    // 목적지 이름을 정하지 않으면 null 이 가능함
    val name: String? = null,
)
