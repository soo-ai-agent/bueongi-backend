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
                key = "street-lamp-fetch-failed",
                title = "가로등 수집 실패",
                detail = "가로등(DSSP-IF-00084) 수집 실패 — 기존 데이터 유지, 다음 요청에서 재시도",
            ),
        )
    }

    fun hadNoCoordinates(fetched: Int) {
        events.publishEvent(
            OperationEvent(
                key = "street-lamp-no-coordinates",
                title = "가로등 좌표 없음",
                detail = "가로등 ${fetched}건을 받았으나 좌표가 없어 적재 0건",
            ),
        )
    }
}
