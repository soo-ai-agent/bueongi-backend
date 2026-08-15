package io.soo.bueongi.core.share.api.request

import io.soo.bueongi.core.share.domain.model.LocationReport

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class UpdateLocationRequest(
    @field:NotNull
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double?,
    @field:NotBlank
    @JsonProperty("owner_secret")
    val ownerSecret: String? = null,
    @field:DecimalMin("0.0")
    @JsonProperty("remaining_m")
    val remainingM: Double? = null,
    @field:DecimalMin("0.0")
    @JsonProperty("remaining_s")
    val remainingS: Double? = null,
) {
    fun toReport(): LocationReport = LocationReport(
        lat = requireNotNull(lat) { "lat 은 @NotNull 검증을 통과했어야 한다" },
        lng = requireNotNull(lng) { "lng 도 동일" },
        remainingM = remainingM,
        remainingS = remainingS,
    )
}
