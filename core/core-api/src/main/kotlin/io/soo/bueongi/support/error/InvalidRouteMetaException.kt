package io.soo.bueongi.support.error

/**
 * 경로 저장(POST /share/{token}/route) 요청의 선택 메타(type/provenance)가 규약을 벗어날 때 던진다 —
 * type 이 허용 집합(safe|fast|main) 밖이거나, provenance 가 최대 길이를 넘을 때. 좌표(@Valid 400)와 달리,
 * "형식은 읽혔으나 값 규약을 못 지킨" 복구 가능 상태라 [io.soo.bueongi.api.ApiControllerAdvice] 가 422 `code:"INVALID_ROUTE_META"` 로
 * 변환한다(RouteRequest 의 ORIGIN_REQUIRED/TRIP_TOO_FAR 와 같은 경계-검증→422 패턴). null(미전송)은 검증 대상이 아니다.
 */
class InvalidRouteMetaException(
    /** 거부된 필드 경로("type" | "provenance"). 응답 errors.field 로 전달한다. */
    val field: String,
    /** 사람이 읽을 수 있는 사유. 응답 detail·errors.message 로 전달한다. */
    override val message: String,
    /** 거부된 값(type 은 짧아 값 자체, provenance 는 장문 echo 회피를 위해 길이). 응답 errors.rejectedValue 로 전달한다. */
    val rejectedValue: Any?,
) : RuntimeException(message)
