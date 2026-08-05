package io.soo.bueongi.domain.admin

import io.soo.bueongi.support.error.InvalidSlackCategoryException
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.SlackAlertConfig
import org.springframework.stereotype.Service

/** GET/PUT /api/admin/slack-config 응답 — 현재 Slack 알림 설정 요약. */
data class SlackConfigSummary(
    /** webhook 이 env 로 설정됐는가(효과 발송의 전제). */
    val webhookConfigured: Boolean,
    /** 마스킹된 webhook(services 경로 토큰을 T*** , B*** , *** 로 별표). 미설정이면 null. 원문은 절대 노출 안 함. */
    val webhookMasked: String?,
    /** 마스터 발송 토글(효과 발송 = webhookConfigured && enabled). */
    val enabled: Boolean,
    /** 같은 알림 key 재발송 억제 창(분). */
    val throttleMinutes: Int,
    /** 알림 카테고리 5종(고정)과 뮤트 여부. */
    val categories: List<SlackCategoryConfig>,
)
