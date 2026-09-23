package io.soo.bueongi.core.safetyspot.domain.safehouse.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ChildSafeHouseFillEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun fetchFailed(tileCode: String) {
        events.publishEvent(
            OperationEvent(
                key = "child-safe-house-fetch-failed",
                title = "아동안전지킴이집 수집 실패",
                detail = "격자 $tileCode 아동안전지킴이집 수집 실패 — 기존 데이터 유지, 다음 요청에서 재시도",
            ),
        )
    }
}
