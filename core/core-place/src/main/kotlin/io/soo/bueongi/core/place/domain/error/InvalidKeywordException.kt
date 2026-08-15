package io.soo.bueongi.core.place.domain.error

import io.soo.bueongi.core.common.error.ApiException
import io.soo.bueongi.core.common.error.ApiFieldError
import org.springframework.http.HttpStatus

class InvalidKeywordException(
    val maxLength: Int,
    val actualLength: Int,
) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    code = "INVALID_KEYWORD",
    title = "Invalid Keyword",
    detail = "검색어가 허용된 최대 길이를 초과했습니다.",
    fieldErrors = listOf(
        ApiFieldError(
            field = "keyword",
            message = "검색어는 최대 ${maxLength}자까지 입력할 수 있습니다.",
            rejectedValue = actualLength,
        ),
    ),
    message = "검색어가 최대 길이(${maxLength}자)를 초과했습니다. (입력 ${actualLength}자)",
)
