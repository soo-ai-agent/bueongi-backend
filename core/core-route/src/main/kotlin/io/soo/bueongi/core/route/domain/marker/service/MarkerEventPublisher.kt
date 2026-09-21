package io.soo.bueongi.core.route.domain.marker.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class MarkerEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun safetySpotIndexEmpty(detail: String) {
        events.publishEvent(OperationEvent(key = INDEX_EMPTY_KEY, title = INDEX_EMPTY_TITLE, detail = detail))
    }

    companion object {
        private const val INDEX_EMPTY_KEY: String = "safetySpot-index-empty"
        private const val INDEX_EMPTY_TITLE: String = "안심시설 데이터 제공 실패"
    }
}
