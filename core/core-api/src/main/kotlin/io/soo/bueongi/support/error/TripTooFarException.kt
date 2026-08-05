package io.soo.bueongi.support.error

/**
 * compare/facilities 요청의 출발지-도착지 직선거리가 도보 귀가로 보기엔 비현실적으로 멀 때 던진다.
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 422 `code:"TRIP_TOO_FAR"` 로 변환한다. 좌표 범위는 유효(400 아님)하지만
 * 도착지 오선택/위경도 뒤바뀜 등 입력 실수로 보고 사용자가 도착지를 바꿔 복구할 수 있는 상태다.
 */
class TripTooFarException(
    /** 출발지-도착지 직선거리(미터, 반올림). 응답 errors.rejectedValue 로 전달한다. */
    val distanceMeters: Int,
    /** 허용 최대 직선거리(미터). 메시지에 km 로 환산해 안내한다. */
    val maxMeters: Int,
) : RuntimeException("출발지와 도착지가 너무 멉니다(거리 ${distanceMeters}m, 최대 ${maxMeters}m).")
