package io.soo.bueongi.core.common.error

import org.springframework.http.HttpStatus

class InvalidSlackCategoryException(
    val rejectedValue: List<String>,
    val knownKeys: List<String>,
) : ApiException(
    status = HttpStatus.UNPROCESSABLE_ENTITY,
    code = "INVALID_SLACK_CATEGORY",
    title = "Invalid Slack Category",
    detail = "알 수 없는 알림 카테고리입니다. 허용된 카테고리만 뮤트할 수 있습니다.",
    fieldErrors = listOf(
        ApiFieldError(
            field = "mutedCategories",
            message = "허용된 카테고리: ${knownKeys.joinToString(", ")}",
            rejectedValue = rejectedValue,
        ),
    ),
    message = "알 수 없는 알림 카테고리입니다: ${rejectedValue.joinToString(", ")} (허용: ${knownKeys.joinToString(", ")})",
)
