package io.soo.bueongi.core.ads.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/** 선택 항목은 클라이언트가 보내지 않을 수 있다. */
data class AdImpressionRequest(
    @field:NotBlank
    @field:Pattern(regexp = "interstitial|native", message = "placement 는 interstitial 또는 native 여야 합니다.")
    val placement: String?,
)

fun AdImpressionRequest.placementValue(): String =
    requireNotNull(placement) { "placement 는 @NotBlank 검증을 통과했어야 한다" }
