package io.soo.bueongi.support.error

/**
 * 관리자 시설 업로드(POST /api/admin/facilities/{type}/upload)의 path 변수 type 이 알려진 시설 종류(cdnType) 밖일 때 던진다.
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 400 `code:"UNKNOWN_FACILITY_TYPE"` + `errors[{field:"type"}]` 로 변환한다.
 */
class UnknownFacilityTypeException(
    /** 거부된 type 값(경로 변수). 응답 errors.rejectedValue 로 전달한다. */
    val rejectedType: String,
    /** 허용된 시설 종류(cdnType) 전체. 메시지에 안내한다. */
    val knownTypes: List<String>,
) : RuntimeException("알 수 없는 시설 종류입니다: $rejectedType (허용: ${knownTypes.joinToString(", ")})")
