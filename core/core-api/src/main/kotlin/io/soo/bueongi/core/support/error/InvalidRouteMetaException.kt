package io.soo.bueongi.core.support.error

import org.springframework.http.HttpStatus

class InvalidRouteMetaException(
    val field: String,
    override val message: String,
    val rejectedValue: Any?,
) : ApiException(
    status = HttpStatus.UNPROCESSABLE_ENTITY,
    code = "INVALID_ROUTE_META",
    title = "Invalid Route Metadata",
    detail = message,
    fieldErrors = listOf(ApiFieldError(field = field, message = message, rejectedValue = rejectedValue)),
    message = message,
)
