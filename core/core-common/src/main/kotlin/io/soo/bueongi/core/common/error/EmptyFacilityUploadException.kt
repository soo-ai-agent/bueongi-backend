package io.soo.bueongi.core.common.error

import org.springframework.http.HttpStatus

class EmptyFacilityUploadException(
    val received: Int,
) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    code = "VALIDATION_FAILED",
    title = "Validation Failed",
    detail = "업로드에서 유효한 시설 좌표를 찾지 못했습니다. 표준 CSV(헤더 포함)인지 확인해 주세요.",
    fieldErrors = listOf(
        ApiFieldError(
            field = "file",
            message = "유효한 좌표가 있는 데이터 행이 없습니다.",
            rejectedValue = received,
        ),
    ),
    message = "업로드에서 유효한 시설 좌표를 찾지 못했습니다(수신 ${received}행).",
)
