package io.soo.bueongi.api

import io.soo.bueongi.support.error.ApiFieldError
import io.soo.bueongi.support.error.InvalidCoordinateException
import io.soo.bueongi.support.error.InvalidKeywordException
import io.soo.bueongi.support.error.InvalidRouteMetaException
import io.soo.bueongi.support.error.InvalidSlackCategoryException
import io.soo.bueongi.support.error.OriginRequiredException
import io.soo.bueongi.support.error.SameOriginDestinationException
import io.soo.bueongi.support.error.ShareSessionNotFoundException
import io.soo.bueongi.support.error.TripTooFarException
import io.soo.bueongi.support.monitoring.AlertNotifier

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 검증/요청 파싱 실패를 표준 에러 스키마(RFC 7807 [ProblemDetail] + `errors[]`)로 변환한다.
 *
 * 기존엔 Spring 기본 포맷이라 프론트가 status 코드만 신뢰할 수 있었다. 이제 본문에
 * `errors`(field/message/rejectedValue)를 담아, 어떤 좌표 필드가 왜 거부됐는지 사용자에게 표시할 수 있다.
 * 응답 content-type 은 `application/problem+json`(Spring 표준).
 */
@RestControllerAdvice
class ApiControllerAdvice(
    /** 운영 알림(없으면 null 무해) — @WebMvcTest 슬라이스엔 알림 빈이 없어 nullable 로 받는다. */
    private val alertNotifier: AlertNotifier? = null,
) {

    private val log = LoggerFactory.getLogger(ApiControllerAdvice::class.java)

    /** `@Valid` 본문 검증 실패 → 400 + 필드별 `errors`(중첩 필드는 `origin.lat` 형태). */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val errors = ex.bindingResult.fieldErrors.map { fe ->
            ApiFieldError(
                field = fe.field,
                message = fe.defaultMessage ?: "유효하지 않은 값입니다.",
                rejectedValue = fe.rejectedValue,
            )
        }
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "요청 본문 검증에 실패했습니다.",
        ).apply {
            title = "Validation Failed"
            setProperty("code", "VALIDATION_FAILED")
            setProperty("errors", errors)
        }
    }

    /** 본문 누락/JSON 파싱 실패(빈 body, 깨진 JSON, 타입 불일치) → 400. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(@Suppress("UNUSED_PARAMETER") ex: HttpMessageNotReadableException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "요청 본문을 읽을 수 없습니다(JSON 형식 또는 본문 누락을 확인하세요).",
        ).apply {
            title = "Malformed Request"
            setProperty("code", "MALFORMED_REQUEST")
        }

    /**
     * 출발지 미확보 → 422 + `code:"ORIGIN_REQUIRED"`. 잘못된 입력(400)이 아니라 "아직 출발지를 알 수 없는"
     * 복구 가능 상태다. 프론트는 이 코드로 위치 권한 재요청 또는 출발지 직접 선택 흐름을 띄운다.
     */
    @ExceptionHandler(OriginRequiredException::class)
    fun handleOriginRequired(@Suppress("UNUSED_PARAMETER") ex: OriginRequiredException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "출발지를 확인할 수 없습니다. 위치 권한을 허용하거나 출발지를 직접 선택해 주세요.",
        ).apply {
            title = "Origin Required"
            setProperty("code", "ORIGIN_REQUIRED")
        }

    /**
     * 검색어 길이 초과 → 400 + `code:"INVALID_KEYWORD"` + `errors[{field:"keyword"}]`.
     * 빈/공백 검색어는 여전히 빈 목록(에러 아님) 계약을 유지하고, 과도하게 긴 검색어만 외부 API 호출 전에 차단한다.
     * rejectedValue 에는 (장문 입력 echo 회피를 위해) 입력 길이를 담는다.
     */
    @ExceptionHandler(InvalidKeywordException::class)
    fun handleInvalidKeyword(ex: InvalidKeywordException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "검색어가 허용된 최대 길이를 초과했습니다.",
        ).apply {
            title = "Invalid Keyword"
            setProperty("code", "INVALID_KEYWORD")
            setProperty(
                "errors",
                listOf(
                    ApiFieldError(
                        field = "keyword",
                        message = "검색어는 최대 ${ex.maxLength}자까지 입력할 수 있습니다.",
                        rejectedValue = ex.actualLength,
                    ),
                ),
            )
        }

    /**
     * 쿼리 파라미터 좌표 범위 오류 → 400 `code:"INVALID_COORDINATE"`. 본문 좌표 검증(400 VALIDATION_FAILED)과
     * 같은 의미의 입력 오류를 GET 계열(최근접 파출소 등)에서도 동일 에러 계약으로 표면화한다.
     */
    @ExceptionHandler(InvalidCoordinateException::class)
    fun handleInvalidCoordinate(ex: InvalidCoordinateException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.message ?: "위경도 범위가 올바르지 않습니다.",
        ).apply {
            title = "Bad Request"
            setProperty("code", "INVALID_COORDINATE")
        }

    /**
     * 출발지와 도착지가 사실상 같은 지점 → 422 + `code:"SAME_ORIGIN_DESTINATION"` + `errors[{field:"destination"}]`.
     * 좌표 자체는 유효하지만(400 아님) 같은 지점으로의 "귀가 경로"는 의미가 없는 복구 가능 상태다 —
     * 프론트는 이 코드로 도착지 재선택을 유도한다(원인이 destination 이므로 ORIGIN_REQUIRED 와 달리 errors 를 담는다).
     */
    @ExceptionHandler(SameOriginDestinationException::class)
    fun handleSameOriginDestination(ex: SameOriginDestinationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "출발지와 도착지가 동일하거나 너무 가깝습니다. 다른 도착지를 선택해 주세요.",
        ).apply {
            title = "Same Origin And Destination"
            setProperty("code", "SAME_ORIGIN_DESTINATION")
            setProperty(
                "errors",
                listOf(
                    ApiFieldError(
                        field = "destination",
                        message = "도착지는 출발지와 다른 지점이어야 합니다.",
                        rejectedValue = ex.distanceMeters,
                    ),
                ),
            )
        }

    /**
     * 출발지-도착지 직선거리가 도보 귀가로 보기엔 비현실적으로 멀 때 → 422 + `code:"TRIP_TOO_FAR"`
     * + `errors[{field:"destination"}]`. 좌표 자체는 유효(범위 내)하지만, 보통 도착지 오선택이나 위경도 뒤바뀜·
     * 타 지역의 캐시된 GPS 같은 입력 실수다 — "850km, 12000분" 같은 무의미한 경로를 만들기보다 복구 가능 상태로
     * 거부해 프론트가 가까운 도착지 재선택을 유도하게 한다(SAME_ORIGIN_DESTINATION 과 같은 결, 거리 상한 쪽).
     */
    @ExceptionHandler(TripTooFarException::class)
    fun handleTripTooFar(ex: TripTooFarException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "출발지에서 도착지까지 거리가 너무 멉니다. 가까운 도착지를 선택해 주세요.",
        ).apply {
            title = "Trip Too Far"
            setProperty("code", "TRIP_TOO_FAR")
            setProperty(
                "errors",
                listOf(
                    ApiFieldError(
                        field = "destination",
                        message = "도착지는 출발지에서 ${ex.maxMeters / 1000}km 이내여야 합니다.",
                        rejectedValue = ex.distanceMeters,
                    ),
                ),
            )
        }

    /**
     * 경로 저장 요청의 선택 메타(type/provenance) 규약 위반 → 422 + `code:"INVALID_ROUTE_META"` + `errors[{field}]`.
     * 좌표/개수 같은 형식 오류(400 VALIDATION_FAILED)와 달리, 형식은 읽혔으나 값 규약(허용 enum·길이 상한)을 못 지킨
     * 복구 가능 상태라 422 로 낸다(RouteRequest 의 ORIGIN_REQUIRED/TRIP_TOO_FAR 와 같은 경계-검증→422 결).
     */
    @ExceptionHandler(InvalidRouteMetaException::class)
    fun handleInvalidRouteMeta(ex: InvalidRouteMetaException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ex.message,
        ).apply {
            title = "Invalid Route Metadata"
            setProperty("code", "INVALID_ROUTE_META")
            setProperty(
                "errors",
                listOf(
                    ApiFieldError(
                        field = ex.field,
                        message = ex.message,
                        rejectedValue = ex.rejectedValue,
                    ),
                ),
            )
        }

    /**
     * 관리자 Slack 설정 편집의 뮤트 카테고리 key 가 허용 5종 밖 → 422 + `code:"INVALID_SLACK_CATEGORY"` +
     * `errors[{field:"mutedCategories"}]`. 형식(문자열 목록)은 읽혔으나 값 규약(허용 카테고리)을 못 지킨
     * 복구 가능 상태라 422 로 낸다(INVALID_ROUTE_META 와 같은 경계-검증→422 결).
     */
    @ExceptionHandler(InvalidSlackCategoryException::class)
    fun handleInvalidSlackCategory(ex: InvalidSlackCategoryException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "알 수 없는 알림 카테고리입니다. 허용된 카테고리만 뮤트할 수 있습니다.",
        ).apply {
            title = "Invalid Slack Category"
            setProperty("code", "INVALID_SLACK_CATEGORY")
            setProperty(
                "errors",
                listOf(
                    ApiFieldError(
                        field = "mutedCategories",
                        message = "허용된 카테고리: ${ex.knownKeys.joinToString(", ")}",
                        rejectedValue = ex.rejectedValue,
                    ),
                ),
            )
        }

    /**
     * 위치 공유 토큰이 없거나 만료된 상태에서 **위치 갱신(POST /share/{token}/location)** 을 시도 → 404 +
     * `code:"SHARE_SESSION_NOT_FOUND"`. 미매칭 경로(NOT_FOUND)와 구분되는, "경로는 맞지만 그 공유 리소스가 없다"는
     * 도메인 404다. (보호자 조회 GET /share/{token}/location 은 404 대신 항상 200 + `expired:true` 로 응답한다.) errors 는 없다.
     */
    @ExceptionHandler(ShareSessionNotFoundException::class)
    fun handleShareSessionNotFound(@Suppress("UNUSED_PARAMETER") ex: ShareSessionNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "공유 세션을 찾을 수 없거나 만료되었습니다.",
        ).apply {
            title = "Share Session Not Found"
            setProperty("code", "SHARE_SESSION_NOT_FOUND")
        }

    /**
     * 지원하지 않는 HTTP 메서드(예: POST 전용 엔드포인트에 GET) → 405 + `code:"METHOD_NOT_ALLOWED"`.
     * Spring 기본 처리는 `code` 없는(또는 빈) 응답을 내므로, "모든 에러 응답에 code" 계약을 지키도록
     * 표준 스키마로 변환한다. HTTP 규약상 405 에 필요한 `Allow` 헤더(허용 메서드)는 보존한다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail.forStatusAndDetail(
            HttpStatus.METHOD_NOT_ALLOWED,
            "지원하지 않는 요청 메서드입니다.",
        ).apply {
            title = "Method Not Allowed"
            setProperty("code", "METHOD_NOT_ALLOWED")
        }
        val headers = HttpHeaders()
        ex.supportedHttpMethods?.let { headers.allow = it }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers).body(body)
    }

    /**
     * 지원하지 않는 Content-Type(예: JSON 본문 엔드포인트에 text/plain) → 415 + `code:"UNSUPPORTED_MEDIA_TYPE"`.
     * 405 와 같은 이유로 "모든 에러 응답에 code" 계약을 지키도록 표준 스키마로 변환한다.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(@Suppress("UNUSED_PARAMETER") ex: HttpMediaTypeNotSupportedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "지원하지 않는 본문 형식입니다. Content-Type 을 application/json 으로 보내주세요.",
        ).apply {
            title = "Unsupported Media Type"
            setProperty("code", "UNSUPPORTED_MEDIA_TYPE")
        }

    /**
     * 매핑된 핸들러/정적 리소스가 없는 경로(오타·폐기된 엔드포인트 호출) → 404 + `code:"NOT_FOUND"`.
     * Spring Boot 3.2+ 는 미매칭 경로에서 [NoResourceFoundException] 을 던지는데, 기본 처리는 `code` 없는
     * 응답을 내므로 "모든 에러 응답에 code" 계약을 지키도록 표준 스키마로 변환한다. errors 는 없다.
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(@Suppress("UNUSED_PARAMETER") ex: NoResourceFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "요청하신 경로를 찾을 수 없습니다.",
        ).apply {
            title = "Not Found"
            setProperty("code", "NOT_FOUND")
        }

    /**
     * 위에서 처리되지 않은 모든 예외(미처리 런타임 오류 등) → 500 + `code:"INTERNAL_ERROR"`.
     * "모든 에러 응답에 code" 계약을 프레임워크 경계까지 마무리한다(이전엔 500 이 `code` 없는 응답이었음).
     *
     * 안심앱 보안상 응답 본문에는 내부 details(스택트레이스·예외 메시지)를 절대 싣지 않는다 — 대신
     * 디버깅용 전체 스택트레이스는 **서버 로그**로만 남긴다(`log.error`). 즉 dev 가시성과 비노출을 양립시킨다.
     * 더 구체적인 `@ExceptionHandler` 가 항상 우선하므로 기존 검증/프레임워크 에러 계약은 영향받지 않는다.
     */
    @ExceptionHandler(Exception::class)
    fun handleUncaught(ex: Exception): ProblemDetail {
        log.error("처리되지 않은 예외 — 500 INTERNAL_ERROR 로 응답합니다.", ex)
        // 서비스 이상 운영 알림 — 예외 클래스별로 스로틀(같은 원인 폭주 방지). 응답 본문 비노출 원칙은 그대로.
        alertNotifier?.notify(
            key = "api-500:${ex.javaClass.simpleName}",
            title = "처리되지 않은 서버 오류(500)",
            detail = "${ex.javaClass.simpleName}: ${ex.message ?: "메시지 없음"}",
        )
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
        ).apply {
            title = "Internal Server Error"
            setProperty("code", "INTERNAL_ERROR")
        }
    }
}
