package io.soo.bueongi.core.place.api.controller

import io.soo.bueongi.core.common.event.ApiEventPublisher
import io.soo.bueongi.core.place.domain.error.PlaceSearchUnavailableException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// 공통 advice 의 ApiException 핸들러보다 먼저 잡아야 알림이 나간다.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class PlaceControllerAdvice(
    // 알림 없이도 예외 응답은 나가야 해서 선택 주입이다.
    private val apiEvents: ApiEventPublisher? = null,
) {
    // 사용자가 실제로 오류 응답을 받은 순간에만 알린다 — 원인은 도메인이 예외에 담아 보낸다.
    @ExceptionHandler(PlaceSearchUnavailableException::class)
    fun handlePlaceSearchUnavailable(ex: PlaceSearchUnavailableException): ProblemDetail {
        apiEvents?.placeSearchUnavailable(ex.cause?.javaClass?.simpleName ?: "원인 미상")
        return ex.toProblemDetail()
    }
}
