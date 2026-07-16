package io.soo.bueongi.support.error

/**
 * 도메인/입력 오류 어휘(단일 출처). 모두 [io.soo.bueongi.api.ApiControllerAdvice] 가 RFC 7807(ProblemDetail) + `code` 로 변환한다 —
 * 예외 클래스명이 곧 복구 가능 상태의 이름이다(규칙: 도메인 용어를 클래스명에 일관 반영).
 */

/**
 * compare/facilities 요청에 출발지(origin)가 없을 때 던진다(위치 권한 거부/미지원 등).
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 422 `code:"ORIGIN_REQUIRED"` 로 변환한다. 좌표 범위 오류 등 "잘못된 입력"과
 * 구분되는, 사용자 행동으로 복구 가능한 상태임을 나타낸다.
 */
class OriginRequiredException : RuntimeException("출발지(origin)가 필요합니다.")

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

/**
 * 쿼리 파라미터 좌표(lat/lng)가 위경도 범위를 벗어날 때 던진다(최근접 파출소 조회 등 GET 계열).
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 400 `code:"INVALID_COORDINATE"` 로 변환한다 — 본문 좌표(@Valid 400)와 동일한
 * 의미의 오류를 쿼리 파라미터 입력에서도 같은 에러 계약으로 표면화한다.
 */
class InvalidCoordinateException(
    val lat: Double,
    val lng: Double,
) : RuntimeException("위경도 범위가 올바르지 않습니다.") // detail 은 기존 응답 계약값 그대로(좌표는 필드로만 보존)

/**
 * compare/facilities 요청의 출발지와 도착지가 사실상 같은 지점일 때 던진다.
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 422 `code:"SAME_ORIGIN_DESTINATION"` 으로 변환한다. 좌표는 유효하나 같은 지점으로의
 * 경로 비교/안심시설이 무의미한 상태로, 사용자가 도착지를 바꿔 복구할 수 있다(좌표 범위 오류 400 과 구분).
 */
class SameOriginDestinationException(
    /** 출발지-도착지 직선거리(미터, 반올림). 응답 errors.rejectedValue 로 전달한다. */
    val distanceMeters: Int,
) : RuntimeException("출발지와 도착지가 동일하거나 너무 가깝습니다(거리 ${distanceMeters}m).")

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

/**
 * 위치 공유 토큰이 없거나 만료된 상태에서 위치 갱신(POST /share/{token}/location)을 시도할 때 던진다.
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 404 `code:"SHARE_SESSION_NOT_FOUND"` 로 변환한다. 미매칭 경로(NOT_FOUND)와 달리
 * "경로는 유효하나 그 공유 리소스가 없다"는 도메인 상태다. (보호자 조회는 404 대신 `expired:true` 로 응답.)
 */
class ShareSessionNotFoundException : RuntimeException("공유를 찾을 수 없거나 만료되었습니다.")

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

/**
 * 관리자 Slack 설정 편집(PUT /api/admin/slack-config)에서 뮤트 카테고리 key 가 알려진 5종 밖일 때 던진다.
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 422 `code:"INVALID_SLACK_CATEGORY"` 로 변환한다 — 형식(문자열 목록)은
 * 읽혔으나 값 규약(허용 카테고리)을 못 지킨 복구 가능 상태다(INVALID_ROUTE_META 와 같은 경계-검증→422 결).
 */
class InvalidSlackCategoryException(
    /** 거부된(알 수 없는) 카테고리 key 목록(정렬). 응답 errors.rejectedValue 로 전달한다. */
    val rejectedValue: List<String>,
    /** 허용된 카테고리 key 전체. 메시지에 안내한다. */
    val knownKeys: List<String>,
) : RuntimeException(
    "알 수 없는 알림 카테고리입니다: ${rejectedValue.joinToString(", ")} (허용: ${knownKeys.joinToString(", ")})",
)

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

/**
 * 관리자 시설 업로드에서 유효한 시설 좌표가 0건일 때(빈 파일·헤더뿐·좌표 없는 행만) 던진다.
 * [io.soo.bueongi.api.ApiControllerAdvice] 가 400 `code:"VALIDATION_FAILED"` + `errors[{field:"file"}]` 로 변환한다 —
 * 본문 검증 실패(@Valid)와 같은 계약으로, 프론트가 파일을 다시 확인하도록 안내한다. 기존 데이터는 교체하지 않는다.
 */
class EmptyFacilityUploadException(
    /** 파싱된 데이터 행 수(모두 좌표 없음·불량이라 유효 0건). 응답 errors.rejectedValue 로 전달한다. */
    val received: Int,
) : RuntimeException("업로드에서 유효한 시설 좌표를 찾지 못했습니다(수신 ${received}행).")

/** 표준 에러 스키마의 필드별 항목. 프론트가 좌표 입력 폼의 어떤 필드를 어떻게 고쳐야 하는지 안내한다. */
data class ApiFieldError(
    /** 거부된 필드 경로. 중첩 시 `origin.lat`, `destination.lng` 형태. */
    val field: String,
    /** 사람이 읽을 수 있는 사유(검증 메시지). */
    val message: String,
    /** 거부된 실제 값(null 가능). */
    val rejectedValue: Any?,
)
