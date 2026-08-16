package io.soo.bueongi.core.share.domain.error

import io.soo.bueongi.core.common.error.ApiException
import io.soo.bueongi.core.common.error.ApiFieldError
import org.springframework.http.HttpStatus

class InvalidRouteMetaException(
    val field: String,
    override val message: String,
    // 값이 아예 안 온 경우가 있어 비어 있을 수 있다.
    val rejectedValue: Any?,
) : ApiException(
    status = HttpStatus.UNPROCESSABLE_ENTITY,
    code = "INVALID_ROUTE_META",
    title = "Invalid Route Metadata",
    detail = message,
    fieldErrors = listOf(ApiFieldError(field = field, message = message, rejectedValue = rejectedValue)),
    message = message,
)
