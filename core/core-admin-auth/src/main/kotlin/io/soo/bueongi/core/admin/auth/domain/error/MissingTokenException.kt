package io.soo.bueongi.core.admin.auth.domain.error

import io.soo.bueongi.core.common.error.ApiException
import org.springframework.http.HttpStatus

class MissingTokenException : ApiException(
    status = HttpStatus.UNAUTHORIZED,
    code = "UNAUTHORIZED",
    title = "Unauthorized",
    detail = "인증 토큰이 필요합니다.",
    message = "access token missing",
)
