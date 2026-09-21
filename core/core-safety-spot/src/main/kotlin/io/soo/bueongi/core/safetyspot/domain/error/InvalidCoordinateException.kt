package io.soo.bueongi.core.safetyspot.domain.error

import io.soo.bueongi.core.common.error.ApiException
import org.springframework.http.HttpStatus

class InvalidCoordinateException(
    lat: Double,
    lng: Double,
) : ApiException(
        status = HttpStatus.BAD_REQUEST,
        code = "INVALID_COORDINATE",
        title = "Bad Request",
        detail = "위경도 범위가 올바르지 않습니다.",
        message = "위경도 범위 밖: lat=$lat, lng=$lng",
    )
