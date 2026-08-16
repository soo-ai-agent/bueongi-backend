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
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val lat: Double?,
    @field:NotNull
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val lng: Double?,
    @field:NotBlank
    @param:JsonProperty("owner_secret")
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val ownerSecret: String? = null,
    @field:DecimalMin("0.0")
    @param:JsonProperty("remaining_m")
    // 길안내 중이 아니면 null 이 가능함
    val remainingM: Double? = null,
    @field:DecimalMin("0.0")
    @param:JsonProperty("remaining_s")
    // 길안내 중이 아니면 null 이 가능함
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
