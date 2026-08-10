package io.soo.bueongi.support.error

data class ApiFieldError(
    val field: String,
    val message: String,
    val rejectedValue: Any?,
)
