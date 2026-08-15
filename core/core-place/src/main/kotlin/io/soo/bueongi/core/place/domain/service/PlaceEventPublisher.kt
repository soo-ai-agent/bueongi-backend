package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 장소 도메인이 밖에 알릴 일.
 * 사건을 발행만 한다 — 누가 받아 무엇을 하는지(알림·집계 등)는 여기서 정하지 않는다.
 */
@Component
class PlaceEventPublisher(
    private val events: ApplicationEventPublisher,
) {

    /** 외부 장소 검색이 실패해 시드 카탈로그로 답했다. */
    fun placeSearchFellBack(detail: String) {
        events.publishEvent(OperationEvent(key = FELL_BACK_KEY, title = FELL_BACK_TITLE, detail = detail))
    }

    companion object {

        private const val FELL_BACK_KEY: String = "kakao-place-fallback"
        private const val FELL_BACK_TITLE: String = "Kakao 장소검색 실패"
    }
}
