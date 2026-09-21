package io.soo.bueongi.core.share.domain.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ShareEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    // maskedToken 은 앞 6자만 남긴 값이어야 한다(PII 금지).
    fun missedArrivalSuspected(
        maskedToken: String,
        detail: String,
    ) {
        events.publishEvent(
            OperationEvent(key = "$MISSED_ARRIVAL_KEY_PREFIX$maskedToken", title = MISSED_ARRIVAL_TITLE, detail = detail),
        )
    }

    companion object {
        private const val MISSED_ARRIVAL_KEY_PREFIX: String = "missed-arrival:"
        private const val MISSED_ARRIVAL_TITLE: String = "미도착 의심 — 위치 공유"
    }
}
