package io.soo.bueongi.support.error

/**
 * compare/facilities 요청의 출발지와 도착지가 사실상 같은 지점일 때 던진다.
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 422 `code:"SAME_ORIGIN_DESTINATION"` 으로 변환한다. 좌표는 유효하나 같은 지점으로의
 * 경로 비교/안심시설이 무의미한 상태로, 사용자가 도착지를 바꿔 복구할 수 있다(좌표 범위 오류 400 과 구분).
 */
class SameOriginDestinationException(
    /** 출발지-도착지 직선거리(미터, 반올림). 응답 errors.rejectedValue 로 전달한다. */
    val distanceMeters: Int,
) : RuntimeException("출발지와 도착지가 동일하거나 너무 가깝습니다(거리 ${distanceMeters}m).")
