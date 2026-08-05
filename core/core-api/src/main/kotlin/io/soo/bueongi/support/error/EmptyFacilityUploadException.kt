package io.soo.bueongi.support.error

/**
 * 관리자 시설 업로드에서 유효한 시설 좌표가 0건일 때(빈 파일·헤더뿐·좌표 없는 행만) 던진다.
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 400 `code:"VALIDATION_FAILED"` + `errors[{field:"file"}]` 로 변환한다 —
 * 본문 검증 실패(@Valid)와 같은 계약으로, 프론트가 파일을 다시 확인하도록 안내한다. 기존 데이터는 교체하지 않는다.
 */
class EmptyFacilityUploadException(
    /** 파싱된 데이터 행 수(모두 좌표 없음·불량이라 유효 0건). 응답 errors.rejectedValue 로 전달한다. */
    val received: Int,
) : RuntimeException("업로드에서 유효한 시설 좌표를 찾지 못했습니다(수신 ${received}행).")
