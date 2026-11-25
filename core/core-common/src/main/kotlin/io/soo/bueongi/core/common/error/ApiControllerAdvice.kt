package io.soo.bueongi.core.common.error

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.soo.bueongi.core.common.event.ApiErrorResponded
import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.core.enums.AlertCategory
import io.soo.bueongi.support.logging.currentRequestOrigin
import io.soo.bueongi.support.logging.traceColumn
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.BeanInstantiationException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class ApiControllerAdvice(
    private val events: ApplicationEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(ApiControllerAdvice::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ProblemDetail {
        events.publishEvent(ApiErrorResponded(code = ex.code, causeName = ex.cause?.javaClass?.simpleName))
        return ex.toProblemDetail()
    }

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
    fun handleUnreadable(ex: HttpMessageNotReadableException): ProblemDetail {
        val domainCause: ApiException? = ex.findApiCause()
        if (domainCause != null) {
            return handleApi(domainCause)
        }
        val missingField: String? = ex.missingFieldPath()
        if (missingField != null) {
            return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "요청 본문 검증에 실패했습니다.",
            ).apply {
                title = "Validation Failed"
                setProperty("code", "VALIDATION_FAILED")
                setProperty(
                    "errors",
                    listOf(ApiFieldError(field = missingField, message = "널이어서는 안됩니다", rejectedValue = null)),
                )
            }
        }
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "요청 본문을 읽을 수 없습니다(JSON 형식 또는 본문 누락을 확인하세요).",
        ).apply {
            title = "Malformed Request"
            setProperty("code", "MALFORMED_REQUEST")
        }
    }

    private fun HttpMessageNotReadableException.missingFieldPath(): String? {
        val cause: Throwable? = cause
        if (cause !is MismatchedInputException) {
            return null
        }
        val names: MutableList<String> = mutableListOf()
        for (reference in cause.path) {
            val name: String? = reference.fieldName
            if (name != null) {
                names.add(name)
            }
        }
        if (names.isEmpty()) {
            return null
        }
        return names.joinToString(".")
    }

    private fun Throwable.findApiCause(): ApiException? {
        var current: Throwable? = cause
        while (current != null) {
            if (current is ApiException) {
                return current
            }
            if (current.cause === current) {
                return null
            }
            current = current.cause
        }
        return null
    }

    @ExceptionHandler(BeanInstantiationException::class)
    fun handleRequestBinding(ex: BeanInstantiationException): ProblemDetail {
        val cause: Throwable? = ex.cause
        if (cause is ApiException) {
            return handleApi(cause)
        }
        throw ex
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(ex: MissingServletRequestParameterException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "필수 요청 파라미터가 없습니다.",
        ).apply {
            title = "Validation Failed"
            setProperty("code", "VALIDATION_FAILED")
            setProperty(
                "errors",
                listOf(ApiFieldError(field = ex.parameterName, message = "필수 파라미터입니다.", rejectedValue = null)),
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ProblemDetail> {
        val body: ProblemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "지원하지 않는 요청 메서드입니다.",
            ).apply {
                title = "Method Not Allowed"
                setProperty("code", "METHOD_NOT_ALLOWED")
            }
        val headers = HttpHeaders()
        val supportedMethods: Set<HttpMethod>? = ex.supportedHttpMethods
        if (supportedMethods != null) {
            headers.allow = supportedMethods
        }
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

    @ExceptionHandler(Exception::class)
    fun handleUncaught(ex: Exception): ProblemDetail {
        logUnhandled(
            counterpart = "요청 처리",
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            action = "500 INTERNAL_ERROR 응답",
            ex = ex,
        )
        events.publishEvent(
            OperationEvent(
                key = AlertCategory.SERVER_ERROR.keyOf(ex.javaClass.simpleName),
                title = "처리되지 않은 서버 오류(500)",
                detail = "${ex.javaClass.simpleName}: ${ex.message ?: "메시지 없음"}",
            ),
        )
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
        ).apply {
            title = "Internal Server Error"
            setProperty("code", "INTERNAL_ERROR")
        }
    }

    private fun logUnhandled(
        counterpart: String,
        status: Int,
        action: String,
        ex: Exception,
    ) {
        val origin: String = currentRequestOrigin().ifEmpty { UNKNOWN_ORIGIN }
        // 스택에는 외부 호출 URL(인증키 포함)이 섞여 온다 — 예외 종류까지만 남긴다.
        log.error(
            "미처리 예외 | $counterpart | $status | $origin | ${traceColumn()} | " +
                "원인=${ex.javaClass.simpleName} | 조치=$action",
        )
    }

    private companion object {
        const val UNKNOWN_ORIGIN: String = "요청 미상"
    }
}
