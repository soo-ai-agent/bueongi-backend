package io.soo.bueongi.core.share.domain.error

import io.soo.bueongi.core.common.error.ApiException
import org.springframework.http.HttpStatus

class ShareSessionNotFoundException : ApiException(
    status = HttpStatus.NOT_FOUND,
    code = "SHARE_SESSION_NOT_FOUND",
    title = "Share Session Not Found",
    detail = "공유 세션을 찾을 수 없거나 만료되었습니다.",
    message = "공유를 찾을 수 없거나 만료되었습니다.",
)
