package io.soo.bueongi.core.route.domain.error

import io.soo.bueongi.core.common.error.ApiException
import org.springframework.http.HttpStatus

class RouteSearchUnavailableException : ApiException(
    status = HttpStatus.SERVICE_UNAVAILABLE,
    code = "ROUTE_SEARCH_UNAVAILABLE",
    title = "Route Search Unavailable",
    detail = "지금은 경로를 찾을 수 없습니다. 잠시 후 다시 시도해 주세요.",
    message = "경로 외부 연동을 사용할 수 없다.",
)
