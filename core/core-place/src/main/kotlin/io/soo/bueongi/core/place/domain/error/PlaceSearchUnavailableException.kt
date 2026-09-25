package io.soo.bueongi.core.place.domain.error

import io.soo.bueongi.core.common.error.ApiException
import org.springframework.http.HttpStatus

class PlaceSearchUnavailableException(cause: Throwable? = null) : ApiException(
    status = HttpStatus.SERVICE_UNAVAILABLE,
    code = "PLACE_SEARCH_UNAVAILABLE",
    title = "Place Search Unavailable",
    detail = "장소 검색을 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.",
    message = "장소 검색 외부 연동을 사용할 수 없다.",
) {
    init {
        if (cause != null) {
            initCause(cause)
        }
    }
}
