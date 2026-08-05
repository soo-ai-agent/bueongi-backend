package io.soo.bueongi.support.error

/**
 * 쿼리 파라미터 좌표(lat/lng)가 위경도 범위를 벗어날 때 던진다(최근접 파출소 조회 등 GET 계열).
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 400 `code:"INVALID_COORDINATE"` 로 변환한다 — 본문 좌표(@Valid 400)와 동일한
 * 의미의 오류를 쿼리 파라미터 입력에서도 같은 에러 계약으로 표면화한다.
 */
class InvalidCoordinateException(
    val lat: Double,
    val lng: Double,
) : RuntimeException("위경도 범위가 올바르지 않습니다.")
