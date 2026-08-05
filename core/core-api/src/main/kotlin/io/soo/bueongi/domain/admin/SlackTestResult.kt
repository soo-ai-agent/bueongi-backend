package io.soo.bueongi.domain.admin

import io.soo.bueongi.support.error.InvalidSlackCategoryException
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.SlackAlertConfig
import org.springframework.stereotype.Service

/** POST /api/admin/slack-config/test 응답 — 테스트 발송 성공 여부와 현재 효과 발송 가능 여부. */
data class SlackTestResult(
    val sent: Boolean,
    val enabled: Boolean,
)
