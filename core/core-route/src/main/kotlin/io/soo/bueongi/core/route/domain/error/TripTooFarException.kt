package io.soo.bueongi.core.route.domain.error

import io.soo.bueongi.core.common.error.ApiException
import io.soo.bueongi.core.common.error.ApiFieldError
import org.springframework.http.HttpStatus

class TripTooFarException(
    val distanceMeters: Int,
    val maxMeters: Int,
) : ApiException(
        status = HttpStatus.UNPROCESSABLE_ENTITY,
        code = "TRIP_TOO_FAR",
        title = "Trip Too Far",
        detail = "출발지에서 도착지까지 거리가 너무 멉니다. 가까운 도착지를 선택해 주세요.",
        fieldErrors =
            listOf(
                ApiFieldError(
                    field = "destination",
                    message = "도착지는 출발지에서 ${maxMeters / 1000}km 이내여야 합니다.",
                    rejectedValue = distanceMeters,
                ),
            ),
        message = "출발지와 도착지가 너무 멉니다(거리 ${distanceMeters}m, 최대 ${maxMeters}m).",
    )
