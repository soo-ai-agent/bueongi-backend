package io.soo.bueongi.core.admin.safetyspot.domain.error

import io.soo.bueongi.core.common.error.ApiException
import io.soo.bueongi.core.common.error.ApiFieldError
import org.springframework.http.HttpStatus

class InvalidCsvPageException(page: Int, size: Int, maxSize: Int) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    code = "INVALID_CSV_PAGE",
    title = "Bad Request",
    detail = "쪽 번호는 0 이상, 쪽 크기는 1~$maxSize 이어야 합니다.",
    fieldErrors =
        listOf(
            ApiFieldError("page", "0 이상이어야 합니다.", page),
            ApiFieldError("size", "1~$maxSize 이어야 합니다.", size),
        ),
    message = "CSV 쪽 범위 밖: page=$page, size=$size",
)
