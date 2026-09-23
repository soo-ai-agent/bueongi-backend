package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class RouteEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun safeRouteFellBack(detail: String) {
        events.publishEvent(OperationEvent(key = "tmap-route-fallback", title = "Tmap 경로 API 실패", detail = detail))
    }
}
