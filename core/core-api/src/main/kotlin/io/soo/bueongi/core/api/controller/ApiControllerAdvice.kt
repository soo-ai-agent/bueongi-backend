package io.soo.bueongi.core.api.controller

import io.soo.bueongi.support.monitoring.AlertNotifier

import io.soo.bueongi.core.support.error.ApiFieldError
import io.soo.bueongi.core.support.error.EmptyFacilityUploadException
import io.soo.bueongi.core.support.error.InvalidCoordinateException
import io.soo.bueongi.core.support.error.InvalidKeywordException
import io.soo.bueongi.core.support.error.InvalidRouteMetaException
import io.soo.bueongi.core.support.error.InvalidSlackCategoryException
import io.soo.bueongi.core.support.error.OriginRequiredException
import io.soo.bueongi.core.support.error.SameOriginDestinationException
import io.soo.bueongi.core.support.error.ShareSessionNotFoundException
import io.soo.bueongi.core.support.error.TripTooFarException
import io.soo.bueongi.core.support.error.UnknownFacilityTypeException
import io.soo.bueongi.storage.db.core.share.LocationStoreUnavailableException
import io.soo.bueongi.core.api.auth.AdminAuthenticationException

import org.slf4j.Logger
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
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class ApiControllerAdvice(
    private val alertNotifier: AlertNotifier? = null,
) {

    private val log: Logger = LoggerFactory.getLogger(ApiControllerAdvice::class.java)

    @ExceptionHandler(AdminAuthenticationException::class)
    fun handleAdminAuthentication(@Suppress("UNUSED_PARAMETER") ex: AdminAuthenticationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED,
            "아이디 또는 비밀번호가 올바르지 않거나 인증 토큰이 만료되었습니다.",
        ).apply {
            title = "Unauthorized"
            setProperty("code", "UNAUTHORIZED")
        }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val errors: List<ApiFieldError> = ex.bindingResult.fieldErrors.map { fe ->
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

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(@Suppress("UNUSED_PARAMETER") ex: HttpMessageNotReadableException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "요청 본문을 읽을 수 없습니다(JSON 형식 또는 본문 누락을 확인하세요).",
        ).apply {
            title = "Malformed Request"
            setProperty("code", "MALFORMED_REQUEST")
        }

    @ExceptionHandler(OriginRequiredException::class)
    fun handleOriginRequired(@Suppress("UNUSED_PARAMETER") ex: OriginRequiredException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "출발지를 확인할 수 없습니다. 위치 권한을 허용하거나 출발지를 직접 선택해 주세요.",
        ).apply {
            title = "Origin Required"
            setProperty("code", "ORIGIN_REQUIRED")
        }

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

    @ExceptionHandler(InvalidCoordinateException::class)
    fun handleInvalidCoordinate(ex: InvalidCoordinateException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.message ?: "위경도 범위가 올바르지 않습니다.",
        ).apply {
            title = "Bad Request"
            setProperty("code", "INVALID_COORDINATE")
        }

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

    @ExceptionHandler(UnknownFacilityTypeException::class)
    fun handleUnknownFacilityType(ex: UnknownFacilityTypeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "알 수 없는 시설 종류입니다. 허용된 종류로만 업로드할 수 있습니다.",
        ).apply {
            title = "Unknown Facility Type"
            setProperty("code", "UNKNOWN_FACILITY_TYPE")
            setProperty(
                "errors",
                listOf(
                    ApiFieldError(
                        field = "type",
                        message = "허용된 시설 종류: ${ex.knownTypes.joinToString(", ")}",
                        rejectedValue = ex.rejectedType,
                    ),
                ),
            )
        }

    @ExceptionHandler(EmptyFacilityUploadException::class)
    fun handleEmptyFacilityUpload(ex: EmptyFacilityUploadException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "업로드에서 유효한 시설 좌표를 찾지 못했습니다. 표준 CSV(헤더 포함)인지 확인해 주세요.",
        ).apply {
            title = "Validation Failed"
            setProperty("code", "VALIDATION_FAILED")
            setProperty(
                "errors",
                listOf(
                    ApiFieldError(
                        field = "file",
                        message = "유효한 좌표가 있는 데이터 행이 없습니다.",
                        rejectedValue = ex.received,
                    ),
                ),
            )
        }

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(ex: MissingServletRequestPartException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "업로드 파일이 없습니다. multipart/form-data 의 file 파트로 CSV 를 보내주세요.",
        ).apply {
            title = "Validation Failed"
            setProperty("code", "VALIDATION_FAILED")
            setProperty(
                "errors",
                listOf(
                    ApiFieldError(
                        field = "file",
                        message = "업로드 파일(file 파트)이 필요합니다.",
                        rejectedValue = ex.requestPartName,
                    ),
                ),
            )
        }

    @ExceptionHandler(ShareSessionNotFoundException::class)
    fun handleShareSessionNotFound(@Suppress("UNUSED_PARAMETER") ex: ShareSessionNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "공유 세션을 찾을 수 없거나 만료되었습니다.",
        ).apply {
            title = "Share Session Not Found"
            setProperty("code", "SHARE_SESSION_NOT_FOUND")
        }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ProblemDetail> {
        val body: ProblemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.METHOD_NOT_ALLOWED,
            "지원하지 않는 요청 메서드입니다.",
        ).apply {
            title = "Method Not Allowed"
            setProperty("code", "METHOD_NOT_ALLOWED")
        }
        val headers: HttpHeaders = HttpHeaders()
        ex.supportedHttpMethods?.let { headers.allow = it }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers).body(body)
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(@Suppress("UNUSED_PARAMETER") ex: HttpMediaTypeNotSupportedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "지원하지 않는 본문 형식입니다. Content-Type 을 application/json 으로 보내주세요.",
        ).apply {
            title = "Unsupported Media Type"
            setProperty("code", "UNSUPPORTED_MEDIA_TYPE")
        }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(@Suppress("UNUSED_PARAMETER") ex: NoResourceFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "요청하신 경로를 찾을 수 없습니다.",
        ).apply {
            title = "Not Found"
            setProperty("code", "NOT_FOUND")
        }

    @ExceptionHandler(LocationStoreUnavailableException::class)
    fun handleShareStoreUnavailable(ex: LocationStoreUnavailableException): ResponseEntity<ProblemDetail> {
        log.error("위치 공유 저장소 접근 실패 — 503 SHARE_STORE_UNAVAILABLE 로 응답합니다.", ex)
        alertNotifier?.notify(
            key = "share-store-unavailable",
            title = "위치 공유 저장소(Redis) 접근 실패",
            detail = "${ex.cause?.javaClass?.simpleName ?: "unknown"} — 위치 공유 읽기/쓰기가 일시 중단됩니다.",
        )
        val body: ProblemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "일시적으로 위치 공유 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
        ).apply {
            title = "Service Unavailable"
            setProperty("code", "SHARE_STORE_UNAVAILABLE")
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS.toString())
            .body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUncaught(ex: Exception): ProblemDetail {
        log.error("처리되지 않은 예외 — 500 INTERNAL_ERROR 로 응답합니다.", ex)
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

    private companion object {
        const val RETRY_AFTER_SECONDS: Int = 5
    }
}
