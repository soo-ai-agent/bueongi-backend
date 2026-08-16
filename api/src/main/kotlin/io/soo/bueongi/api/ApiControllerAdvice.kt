package io.soo.bueongi.api

import io.soo.bueongi.core.common.error.ApiException
import io.soo.bueongi.core.common.error.ApiFieldError
import io.soo.bueongi.storage.db.core.share.store.LocationStoreUnavailableException

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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class ApiControllerAdvice(
    // 알림 없이도 예외 응답은 나가야 해서 선택 주입이다.
    private val apiEvents: ApiEventPublisher? = null,
) {

    private val log: Logger = LoggerFactory.getLogger(ApiControllerAdvice::class.java)

    /** 응답 내용은 예외가 안다. 여기서는 종류를 확인하지 않고 옮겨 담기만 한다. */
    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(ex.status, ex.detail).apply {
            title = ex.title
            setProperty("code", ex.code)
            if (ex.fieldErrors.isNotEmpty()) {
                setProperty("errors", ex.fieldErrors)
            }
        }

    /** 경로변수·쿼리 값이 선언 타입으로 안 바뀔 때(`?lat=abc`). 핸들러가 없으면 500 으로 샌다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "요청 값의 형식이 올바르지 않습니다.",
        ).apply {
            title = "Validation Failed"
            setProperty("code", "VALIDATION_FAILED")
            setProperty(
                "errors",
                listOf(ApiFieldError(field = ex.name, message = "형식이 올바르지 않습니다.", rejectedValue = ex.value)),
            )
        }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val errors: ArrayList<ApiFieldError> = ArrayList()
        for (fieldError in ex.bindingResult.fieldErrors) {
            errors.add(
                ApiFieldError(
                    field = fieldError.field,
                    message = fieldError.defaultMessage ?: "유효하지 않은 값입니다.",
                    rejectedValue = fieldError.rejectedValue,
                ),
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
    fun handleUnreadable(ex: HttpMessageNotReadableException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "요청 본문을 읽을 수 없습니다(JSON 형식 또는 본문 누락을 확인하세요).",
        ).apply {
            title = "Malformed Request"
            setProperty("code", "MALFORMED_REQUEST")
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ProblemDetail> {
        val body: ProblemDetail = ProblemDetail.forStatusAndDetail(
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

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(ex: HttpMediaTypeNotSupportedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "지원하지 않는 본문 형식입니다. Content-Type 을 application/json 으로 보내주세요.",
        ).apply {
            title = "Unsupported Media Type"
            setProperty("code", "UNSUPPORTED_MEDIA_TYPE")
        }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(ex: NoResourceFoundException): ProblemDetail =
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
        apiEvents?.shareStoreUnavailable(
            "${ex.cause?.javaClass?.simpleName ?: "unknown"} — 위치 공유 읽기/쓰기가 일시 중단됩니다.",
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
        apiEvents?.unhandledServerError(
            exceptionName = ex.javaClass.simpleName,
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
