package io.soo.bueongi.domain.admin

import io.soo.bueongi.support.error.InvalidSlackCategoryException
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.SlackAlertConfig
import org.springframework.stereotype.Service

/** 알림 카테고리 1건 — key(접두사)·라벨·뮤트 여부. */
data class SlackCategoryConfig(
    val key: String,
    val label: String,
    val muted: Boolean,
)
