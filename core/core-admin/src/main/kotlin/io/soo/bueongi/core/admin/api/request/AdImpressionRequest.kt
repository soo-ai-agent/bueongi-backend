package io.soo.bueongi.core.admin.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class AdImpressionRequest(
    @field:NotBlank
    @field:Pattern(regexp = "interstitial|native", message = "placement 는 interstitial 또는 native 여야 합니다.")
    val placement: String?,
)

fun AdImpressionRequest.placementValue(): String =
    requireNotNull(placement) { "placement 는 @NotBlank 검증을 통과했어야 한다" }
