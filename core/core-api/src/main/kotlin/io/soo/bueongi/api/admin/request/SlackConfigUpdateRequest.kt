package io.soo.bueongi.api.admin.request

import io.soo.bueongi.domain.admin.SlackConfigUpdate
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

/**
 * `PUT /api/admin/slack-config` 요청 DTO — 관리자가 편집할 수 있는 값만 받는다.
 * **webhook URL 은 담기지 않는다**(env 시크릿, 편집 불가). 검증(@Valid)과 도메인 입력 변환([toUpdate])까지가
 * 이 파일의 책임이다 — 도메인 서비스는 이 웹 DTO 를 모른 채 [SlackConfigUpdate] 만 받는다(의존 방향 준수).
 */
data class SlackConfigUpdateRequest(
    /** 마스터 발송 토글. 필수(누락 → 400 VALIDATION_FAILED). */
    @field:NotNull
    val enabled: Boolean?,
    /** 같은 알림 key 재발송 억제 창(분). 필수 + [1, 1440] 범위(밖이면 400 VALIDATION_FAILED). */
    @field:NotNull
    @field:Min(1) @field:Max(1440)
    val throttleMinutes: Int?,
    /** 뮤트할 카테고리 key 목록(발송만 생략, 이력은 남음). 누락/null → 뮤트 없음. 알 수 없는 key 는 422. */
    val mutedCategories: List<String>? = null,
) {
    /**
     * 웹 요청 → 도메인 입력([SlackConfigUpdate]) 변환. @Valid(@NotNull) 통과 후 호출되므로 enabled/throttleMinutes 는
     * 비null 이 보장된다(requireNotNull 은 도달 불가 방어선). mutedCategories 는 중복 제거해 집합으로 넘긴다.
     */
    fun toUpdate(): SlackConfigUpdate = SlackConfigUpdate(
        enabled = requireNotNull(enabled) { "enabled 는 @NotNull 검증을 통과했어야 한다" },
        throttleMinutes = requireNotNull(throttleMinutes) { "throttleMinutes 는 @NotNull 검증을 통과했어야 한다" },
        mutedCategories = mutedCategories.orEmpty().toSet(),
    )
}
