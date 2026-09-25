package io.soo.bueongi.core.admin.safetyspot.domain.error

import io.soo.bueongi.core.common.error.ApiException
import io.soo.bueongi.core.common.error.ApiFieldError
import org.springframework.http.HttpStatus

class InvalidSpotLimitException(limit: Int) : ApiException(
        status = HttpStatus.BAD_REQUEST,
        code = "INVALID_SPOT_LIMIT",
        title = "Bad Request",
        detail = "표시 개수는 1 이상이어야 합니다.",
        fieldErrors = listOf(ApiFieldError("limit", "1 이상이어야 합니다.", limit)),
        message = "표시 개수 범위 밖: limit=$limit",
    )
