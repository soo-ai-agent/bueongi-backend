package io.soo.bueongi.core.common.error

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

abstract class ApiException(
    val status: HttpStatus,
    val code: String,
    val title: String,
    val detail: String,
    val fieldErrors: List<ApiFieldError> = emptyList(),
    message: String,
) : RuntimeException(message) {
    fun toProblemDetail(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            title = this@ApiException.title
            setProperty("code", code)
            if (fieldErrors.isNotEmpty()) {
                setProperty("errors", fieldErrors)
            }
        }
}
