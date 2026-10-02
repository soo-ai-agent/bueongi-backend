package io.soo.bueongi.core.route.api.controller

import io.soo.bueongi.core.common.event.ApiEventPublisher
import io.soo.bueongi.core.route.domain.error.RouteSearchUnavailableException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// 공통 advice 의 ApiException 핸들러보다 먼저 잡아야 알림이 나간다.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class RouteControllerAdvice(
    // 알림 없이도 예외 응답은 나가야 해서 선택 주입이다.
    private val apiEvents: ApiEventPublisher? = null,
) {
    // 사용자가 실제로 오류 응답을 받은 순간에만 알린다.
    @ExceptionHandler(RouteSearchUnavailableException::class)
    fun handleRouteSearchUnavailable(ex: RouteSearchUnavailableException): ProblemDetail {
        apiEvents?.routeSearchUnavailable("경로 후보 전멸 또는 키 미설정 — 앱은 503 안내를 받는다.")
        return ex.toProblemDetail()
    }
}
