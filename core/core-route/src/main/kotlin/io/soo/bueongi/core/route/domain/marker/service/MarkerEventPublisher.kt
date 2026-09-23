package io.soo.bueongi.core.route.domain.marker.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class MarkerEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun safetySpotIndexEmpty(detail: String) {
        events.publishEvent(OperationEvent(key = "safetySpot-index-empty", title = "안심시설 데이터 제공 실패", detail = detail))
    }
}
