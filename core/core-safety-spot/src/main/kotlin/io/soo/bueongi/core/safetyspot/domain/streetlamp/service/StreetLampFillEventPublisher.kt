package io.soo.bueongi.core.safetyspot.domain.streetlamp.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class StreetLampFillEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun fetchFailed() {
        events.publishEvent(
            OperationEvent(
                key = FETCH_FAILED_KEY,
                title = FETCH_FAILED_TITLE,
                detail = "가로등(DSSP-IF-00084) 수집 실패 — 기존 데이터 유지, 다음 요청에서 재시도",
            ),
        )
    }

    fun hadNoCoordinates(fetched: Int) {
        events.publishEvent(
            OperationEvent(
                key = NO_COORDINATES_KEY,
                title = NO_COORDINATES_TITLE,
                detail = "가로등 ${fetched}건을 받았으나 좌표가 없어 적재 0건",
            ),
        )
    }

    companion object {
        private const val FETCH_FAILED_KEY: String = "street-lamp-fetch-failed"
        private const val FETCH_FAILED_TITLE: String = "가로등 수집 실패"
        private const val NO_COORDINATES_KEY: String = "street-lamp-no-coordinates"
        private const val NO_COORDINATES_TITLE: String = "가로등 좌표 없음"
    }
}
