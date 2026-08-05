package io.soo.bueongi.domain.admin

import io.soo.bueongi.support.error.InvalidSlackCategoryException
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.SlackAlertConfig
import org.springframework.stereotype.Service

/**
 * Slack 설정 편집 입력(도메인 값) — 웹 요청 DTO(@Valid 통과)에서 변환된 비null 값만 담는다.
 * 컨트롤러가 요청을 이 값으로 변환해 넘기므로 도메인 서비스는 웹 DTO 를 모른다(의존 방향 준수).
 */
data class SlackConfigUpdate(
    val enabled: Boolean,
    val throttleMinutes: Int,
    val mutedCategories: Set<String>,
)
