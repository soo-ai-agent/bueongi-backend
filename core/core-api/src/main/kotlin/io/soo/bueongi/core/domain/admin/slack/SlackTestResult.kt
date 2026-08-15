package io.soo.bueongi.core.domain.admin.slack

import io.soo.bueongi.core.support.error.InvalidSlackCategoryException
import org.springframework.stereotype.Service

data class SlackTestResult(
    val sent: Boolean,
    val enabled: Boolean,
)
