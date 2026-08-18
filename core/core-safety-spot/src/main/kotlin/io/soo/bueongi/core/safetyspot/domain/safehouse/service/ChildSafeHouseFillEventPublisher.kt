package io.soo.bueongi.core.safetyspot.domain.safehouse.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 아동안전지킴이집 채우기가 밖에 알릴 일.
 * 사건을 발행만 한다 — 누가 받아 무엇을 하는지(알림·집계 등)는 여기서 정하지 않는다.
 */
@Component
class ChildSafeHouseFillEventPublisher(
    private val events: ApplicationEventPublisher,
) {

    /** 수집이 실패했다 — 다음 요청에서 다시 시도된다. 일일 한도 초과도 여기로 온다. */
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
