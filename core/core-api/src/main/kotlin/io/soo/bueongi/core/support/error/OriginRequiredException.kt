package io.soo.bueongi.core.support.error

import org.springframework.http.HttpStatus

class OriginRequiredException : ApiException(
    status = HttpStatus.UNPROCESSABLE_ENTITY,
    code = "ORIGIN_REQUIRED",
    title = "Origin Required",
    detail = "출발지를 확인할 수 없습니다. 위치 권한을 허용하거나 출발지를 직접 선택해 주세요.",
    message = "출발지(origin)가 필요합니다.",
)
