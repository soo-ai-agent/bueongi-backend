package io.soo.bueongi.admin

import io.soo.bueongi.common.error.InvalidSlackCategoryException
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.SlackAlertConfig
import org.springframework.stereotype.Service

data class SlackTestResult(
    val sent: Boolean,
    val enabled: Boolean,
)
