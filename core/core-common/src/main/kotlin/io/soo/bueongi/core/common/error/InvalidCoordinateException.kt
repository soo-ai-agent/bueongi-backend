package io.soo.bueongi.core.common.error

import org.springframework.http.HttpStatus

class InvalidCoordinateException(
    val lat: Double,
    val lng: Double,
) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    code = "INVALID_COORDINATE",
    title = "Bad Request",
    detail = "위경도 범위가 올바르지 않습니다.",
    message = "위경도 범위가 올바르지 않습니다.",
)
