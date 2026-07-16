package io.soo.bueongi.api.ads.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * POST /api/ads/impression 요청 — 어느 광고 지면([placement])이 노출됐는지.
 * 허용값은 `interstitial`|`native` 두 가지이며, 검증은 이 DTO 계층에서 끝낸다(컨트롤러 수동 검증 없음):
 *  - 누락/공백 → @NotBlank → 400 VALIDATION_FAILED
 *  - 허용값 밖 → @Pattern → 400 VALIDATION_FAILED
 * (허용 지면 집합은 [io.soo.bueongi.domain.admin.AdStatsService.TRACKED_PLACEMENTS] 와 같다.)
 */
data class AdImpressionRequest(
    @field:NotBlank
    @field:Pattern(regexp = "interstitial|native", message = "placement 는 interstitial 또는 native 여야 합니다.")
    val placement: String?,
)

/** @NotBlank/@Pattern 검증을 통과한 뒤에만 호출된다 — requireNotNull 은 도달 불가 방어선(검증 우회 버그 표면화). */
fun AdImpressionRequest.placementValue(): String =
    requireNotNull(placement) { "placement 는 @NotBlank 검증을 통과했어야 한다" }
