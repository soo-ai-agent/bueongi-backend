package io.soo.bueongi.core.admin.auth.domain.error

import io.soo.bueongi.core.common.error.ApiException
import org.springframework.http.HttpStatus

class InvalidTokenException(
    message: String,
    cause: Throwable? = null,
) : ApiException(
    status = HttpStatus.UNAUTHORIZED,
    code = "UNAUTHORIZED",
    title = "Unauthorized",
    detail = "유효하지 않은 인증 토큰입니다.",
    message = message,
) {
    init {
        if (cause != null) {
            initCause(cause)
        }
    }
}
