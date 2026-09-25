package io.soo.bueongi.core.common.error

data class ApiFieldError(val field: String, val message: String, val rejectedValue: Any?)
