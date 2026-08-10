package io.soo.bueongi.support.error

class InvalidSlackCategoryException(
    val rejectedValue: List<String>,
    val knownKeys: List<String>,
) : RuntimeException(
    "알 수 없는 알림 카테고리입니다: ${rejectedValue.joinToString(", ")} (허용: ${knownKeys.joinToString(", ")})",
)
