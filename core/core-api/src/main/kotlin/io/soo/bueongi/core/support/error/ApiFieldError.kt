package io.soo.bueongi.core.support.error

data class ApiFieldError(
    val field: String,
    val message: String,
    val rejectedValue: Any?,
)
