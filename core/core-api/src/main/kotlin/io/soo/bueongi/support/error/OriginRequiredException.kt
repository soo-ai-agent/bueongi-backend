package io.soo.bueongi.support.error

/**
 * compare/facilities 요청에 출발지(origin)가 없을 때 던진다(위치 권한 거부/미지원 등).
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 422 `code:"ORIGIN_REQUIRED"` 로 변환한다. 좌표 범위 오류 등 "잘못된 입력"과
 * 구분되는, 사용자 행동으로 복구 가능한 상태임을 나타낸다.
 */
class OriginRequiredException : RuntimeException("출발지(origin)가 필요합니다.")
