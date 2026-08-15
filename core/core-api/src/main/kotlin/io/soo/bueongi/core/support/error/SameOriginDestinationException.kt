package io.soo.bueongi.core.support.error

import org.springframework.http.HttpStatus

class SameOriginDestinationException(
    val distanceMeters: Int,
) : ApiException(
    status = HttpStatus.UNPROCESSABLE_ENTITY,
    code = "SAME_ORIGIN_DESTINATION",
    title = "Same Origin And Destination",
    detail = "출발지와 도착지가 동일하거나 너무 가깝습니다. 다른 도착지를 선택해 주세요.",
    fieldErrors = listOf(
        ApiFieldError(
            field = "destination",
            message = "도착지는 출발지와 다른 지점이어야 합니다.",
            rejectedValue = distanceMeters,
        ),
    ),
    message = "출발지와 도착지가 동일하거나 너무 가깝습니다(거리 ${distanceMeters}m).",
)
