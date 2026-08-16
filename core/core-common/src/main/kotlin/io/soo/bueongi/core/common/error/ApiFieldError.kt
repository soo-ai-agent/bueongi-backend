package io.soo.bueongi.core.common.error

/** 거부된 값은 로그에 남기지 않는 필드가 있다. */
data class ApiFieldError(
    val field: String,
    val message: String,
    // 값이 아예 안 온 경우가 있어 비어 있을 수 있다.
    val rejectedValue: Any?,
)
