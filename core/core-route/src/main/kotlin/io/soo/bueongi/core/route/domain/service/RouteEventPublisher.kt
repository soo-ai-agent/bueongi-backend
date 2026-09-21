package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class RouteEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun safeRouteFellBack(detail: String) {
        events.publishEvent(OperationEvent(key = FELL_BACK_KEY, title = FELL_BACK_TITLE, detail = detail))
    }

    companion object {
        private const val FELL_BACK_KEY: String = "tmap-route-fallback"
        private const val FELL_BACK_TITLE: String = "Tmap 경로 API 실패"
    }
}
