package io.soo.bueongi.core.domain.admin

import io.soo.bueongi.core.support.error.InvalidSlackCategoryException
import org.springframework.stereotype.Service

data class SlackCategoryConfig(
    val key: String,
    val label: String,
    val muted: Boolean,
)
