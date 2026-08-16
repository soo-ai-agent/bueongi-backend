package io.soo.bueongi.core.share.api.request

import io.soo.bueongi.core.share.domain.model.LocationReport

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** 빠진 값도 받아야 어느 필드가 틀렸는지 응답에 담긴다. 나머지는 선택 항목이다. */
data class UpdateLocationRequest(
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
    @field:NotBlank
    @param:JsonProperty("owner_secret")
    val ownerSecret: String? = null,
    @field:DecimalMin("0.0")
    @param:JsonProperty("remaining_m")
    val remainingM: Double? = null,
    @field:DecimalMin("0.0")
    @param:JsonProperty("remaining_s")
    val remainingS: Double? = null,
) {
    fun toReport(): LocationReport =
        LocationReport(
            lat = requireNotNull(lat) { "lat 은 @NotNull 검증을 통과했어야 한다" },
            lng = requireNotNull(lng) { "lng 도 동일" },
            remainingM = remainingM,
            remainingS = remainingS,
        )
}
