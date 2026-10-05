package io.soo.bueongi.core.admin.auth.domain.error

import io.soo.bueongi.core.common.error.ApiException
import org.springframework.http.HttpStatus

class AdminRoleRequiredException : ApiException(
    status = HttpStatus.FORBIDDEN,
    code = "FORBIDDEN",
    title = "Forbidden",
    detail = "관리자 권한이 필요합니다.",
    message = "admin role required",
)
