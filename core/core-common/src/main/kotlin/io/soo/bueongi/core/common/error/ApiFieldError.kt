package io.soo.bueongi.core.common.error

data class ApiFieldError(
    val field: String,
    val message: String,
    // 거절된 값을 받은 JSON 타입 그대로(숫자·문자열·목록·없음) 되돌려 준다 — Spring FieldError.rejectedValue 도 Object 다.
    val rejectedValue: Any?,
)
