package io.soo.bueongi.core.share.domain.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 위치 공유 도메인이 밖에 알릴 일.
 * 사건을 발행만 한다 — 누가 받아 무엇을 하는지(알림·집계 등)는 여기서 정하지 않는다.
 */
@Component
class ShareEventPublisher(
    private val events: ApplicationEventPublisher,
) {

    /**
     * 예정 도착 시간을 넘겼는데 위치 보고도 끊겼다.
     * [maskedToken] 은 앞 6자만 남긴 값이다(PII 금지) — 세션마다 키를 갈라 스로틀이 서로를 삼키지 않게 한다.
     */
    fun missedArrivalSuspected(maskedToken: String, detail: String) {
        events.publishEvent(
            OperationEvent(key = "$MISSED_ARRIVAL_KEY_PREFIX$maskedToken", title = MISSED_ARRIVAL_TITLE, detail = detail),
        )
    }

    companion object {

        private const val MISSED_ARRIVAL_KEY_PREFIX: String = "missed-arrival:"
        private const val MISSED_ARRIVAL_TITLE: String = "미도착 의심 — 위치 공유"
    }
}
