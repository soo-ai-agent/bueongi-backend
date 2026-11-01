package io.soo.bueongi.core.admin.auth.domain.error

import io.soo.bueongi.core.common.error.ApiException
import org.springframework.http.HttpStatus

class ExpiredTokenException(
    cause: Throwable? = null,
) : ApiException(
        status = HttpStatus.UNAUTHORIZED,
        code = "UNAUTHORIZED",
        title = "Unauthorized",
        detail = "인증 토큰이 만료되었습니다.",
        message = "access token expired",
    ) {
    init {
        if (cause != null) {
            initCause(cause)
        }
    }
}
