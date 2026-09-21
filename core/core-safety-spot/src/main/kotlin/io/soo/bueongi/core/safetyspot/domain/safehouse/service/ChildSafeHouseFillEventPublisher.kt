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
                key = FETCH_FAILED_KEY,
                title = FETCH_FAILED_TITLE,
                detail = "격자 $tileCode 아동안전지킴이집 수집 실패 — 기존 데이터 유지, 다음 요청에서 재시도",
            ),
        )
    }

    companion object {
        private const val FETCH_FAILED_KEY: String = "child-safe-house-fetch-failed"
        private const val FETCH_FAILED_TITLE: String = "아동안전지킴이집 수집 실패"
    }
}
