package io.soo.bueongi.core.auth.domain

import io.soo.bueongi.core.common.error.ApiException
import org.springframework.http.HttpStatus

class AdminAuthenticationException : ApiException(
    status = HttpStatus.UNAUTHORIZED,
    code = "UNAUTHORIZED",
    title = "Unauthorized",
    detail = "아이디 또는 비밀번호가 올바르지 않거나 인증 토큰이 만료되었습니다.",
    message = "관리자 인증에 실패했습니다.",
)
