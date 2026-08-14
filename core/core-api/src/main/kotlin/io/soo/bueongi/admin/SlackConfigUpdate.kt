package io.soo.bueongi.admin

import io.soo.bueongi.support.error.InvalidSlackCategoryException
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.SlackAlertConfig
import org.springframework.stereotype.Service

data class SlackConfigUpdate(
    val enabled: Boolean,
    val throttleMinutes: Int,
    val mutedCategories: Set<String>,
)
