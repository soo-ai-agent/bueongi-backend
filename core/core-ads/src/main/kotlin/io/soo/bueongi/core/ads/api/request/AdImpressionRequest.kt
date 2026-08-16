package io.soo.bueongi.core.ads.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class AdImpressionRequest(
    @field:NotBlank
    @field:Pattern(regexp = "interstitial|native", message = "placement 는 interstitial 또는 native 여야 합니다.")
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val placement: String?,
)

fun AdImpressionRequest.placementValue(): String =
    requireNotNull(placement) { "placement 는 @NotBlank 검증을 통과했어야 한다" }
