package io.soo.bueongi.api.v1.request

import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.support.error.InvalidCoordinateException

/**
 * GET 쿼리 파라미터 좌표의 경계 검증 — 본문 DTO 의 `@Valid`/`toValidatedQuery` 가 하는 역할을
 * 쿼리 파라미터에도 같은 계층(request)에서 제공한다("컨트롤러 안 수동 검증 금지" 규칙의 쿼리 버전).
 *
 * 범위(위도 ±90 / 경도 ±180) 밖이면 400 `INVALID_COORDINATE` — 본문 좌표 검증과 동일한 에러 계약.
 * 통과하면 비null [GeoPoint] 를 돌려주므로 도메인은 검증된 좌표만 받는다.
 */
fun toValidatedGeoPoint(lat: Double, lng: Double): GeoPoint {
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) throw InvalidCoordinateException(lat, lng)
    return GeoPoint(lat, lng)
}
