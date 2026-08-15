package io.soo.bueongi.core.common.error

import org.springframework.http.HttpStatus

class UnknownFacilityTypeException(
    val rejectedType: String,
    val knownTypes: List<String>,
) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    code = "UNKNOWN_FACILITY_TYPE",
    title = "Unknown Facility Type",
    detail = "알 수 없는 시설 종류입니다. 허용된 종류로만 업로드할 수 있습니다.",
    fieldErrors = listOf(
        ApiFieldError(
            field = "type",
            message = "허용된 시설 종류: ${knownTypes.joinToString(", ")}",
            rejectedValue = rejectedType,
        ),
    ),
    message = "알 수 없는 시설 종류입니다: $rejectedType (허용: ${knownTypes.joinToString(", ")})",
)
