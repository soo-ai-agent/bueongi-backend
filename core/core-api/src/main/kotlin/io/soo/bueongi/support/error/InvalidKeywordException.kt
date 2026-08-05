package io.soo.bueongi.support.error

/**
 * 장소 검색어가 허용 최대 길이를 초과할 때 던진다. [io.soo.bueongi.api.ApiControllerAdvice] 가 400 `code:"INVALID_KEYWORD"` 로 변환한다.
 * 빈/공백 검색어(빈 목록 계약)와 구분되는, 사용자가 입력을 줄여 복구할 수 있는 잘못된 입력이다.
 */
class InvalidKeywordException(
    /** 허용된 최대 길이(자). */
    val maxLength: Int,
    /** 거부된 입력의 길이(자). 장문 echo 회피를 위해 값 대신 길이만 보고한다. */
    val actualLength: Int,
) : RuntimeException("검색어가 최대 길이(${maxLength}자)를 초과했습니다. (입력 ${actualLength}자)")
