package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class PlaceEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun placeSearchFellBack(detail: String) {
        events.publishEvent(OperationEvent(key = FELL_BACK_KEY, title = FELL_BACK_TITLE, detail = detail))
    }

    companion object {
        private const val FELL_BACK_KEY: String = "kakao-place-fallback"
        private const val FELL_BACK_TITLE: String = "Kakao 장소검색 실패"
    }
}
