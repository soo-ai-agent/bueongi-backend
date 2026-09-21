package io.soo.bueongi.core.common.error

import org.springframework.http.HttpStatus

abstract class ApiException(
    val status: HttpStatus,
    val code: String,
    val title: String,
    val detail: String,
    val fieldErrors: List<ApiFieldError> = emptyList(),
    message: String,
) : RuntimeException(message)
