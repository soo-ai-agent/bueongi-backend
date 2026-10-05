package io.soo.bueongi.core.admin.safetyspot.domain.error

import io.soo.bueongi.core.common.error.ApiException
import org.springframework.http.HttpStatus

class SafetySpotCsvFileNotFoundException(
    name: String,
) : ApiException(
        status = HttpStatus.NOT_FOUND,
        code = "SAFETY_SPOT_CSV_FILE_NOT_FOUND",
        title = "Not Found",
        detail = "CSV 파일을 찾지 못했습니다.",
        message = "CSV 파일 없음: name=$name",
    )
