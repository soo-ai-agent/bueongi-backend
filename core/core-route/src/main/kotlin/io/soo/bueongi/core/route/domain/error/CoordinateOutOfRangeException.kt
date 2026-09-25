package io.soo.bueongi.core.route.domain.error

import io.soo.bueongi.core.common.error.ApiException
import io.soo.bueongi.core.common.error.ApiFieldError
import org.springframework.http.HttpStatus

class CoordinateOutOfRangeException(field: String, rejectedValue: Double) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    code = "VALIDATION_FAILED",
    title = "Validation Failed",
    detail = "요청 본문 검증에 실패했습니다.",
    fieldErrors =
        listOf(
            ApiFieldError(
                field = field,
                message = "좌표가 허용 범위를 벗어났습니다",
                rejectedValue = rejectedValue,
            ),
        ),
    message = "$field 가 허용 범위를 벗어났습니다(값 $rejectedValue).",
)
