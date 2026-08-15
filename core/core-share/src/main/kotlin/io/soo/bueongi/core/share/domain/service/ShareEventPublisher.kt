package io.soo.bueongi.core.share.domain.service

import io.soo.bueongi.core.common.event.MissedArrivalSuspected
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

    /** 예정 도착 시간을 넘겼는데 위치 보고도 끊겼다. */
    fun missedArrivalSuspected(maskedToken: String, detail: String) {
        events.publishEvent(MissedArrivalSuspected(maskedToken = maskedToken, detail = detail))
    }
}
