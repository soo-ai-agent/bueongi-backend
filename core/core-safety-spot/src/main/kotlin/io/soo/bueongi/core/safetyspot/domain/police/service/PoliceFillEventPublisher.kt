package io.soo.bueongi.core.safetyspot.domain.police.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class PoliceFillEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun fetchFailed() {
        events.publishEvent(
            OperationEvent(
                key = FETCH_FAILED_KEY,
                title = FETCH_FAILED_TITLE,
                detail = "치안시설(IF_0036) 수집 실패 — 기존 데이터 유지, 다음 요청에서 재시도",
            ),
        )
    }

    fun hadNoCoordinates(fetched: Int) {
        events.publishEvent(
            OperationEvent(
                key = NO_COORDINATES_KEY,
                title = NO_COORDINATES_TITLE,
                detail = "치안시설 ${fetched}건을 받았으나 좌표가 없어 적재 0건",
            ),
        )
    }

    companion object {
        private const val FETCH_FAILED_KEY: String = "police-fetch-failed"
        private const val FETCH_FAILED_TITLE: String = "치안시설 수집 실패"
        private const val NO_COORDINATES_KEY: String = "police-no-coordinates"
        private const val NO_COORDINATES_TITLE: String = "치안시설 좌표 없음"
    }
}
