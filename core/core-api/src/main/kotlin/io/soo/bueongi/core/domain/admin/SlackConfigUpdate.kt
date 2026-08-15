package io.soo.bueongi.core.domain.admin

import io.soo.bueongi.core.support.error.InvalidSlackCategoryException
import org.springframework.stereotype.Service

data class SlackConfigUpdate(
    val enabled: Boolean,
    val throttleMinutes: Int,
    val mutedCategories: Set<String>,
)
