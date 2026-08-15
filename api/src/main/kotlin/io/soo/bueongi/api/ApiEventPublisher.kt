package io.soo.bueongi.api

import io.soo.bueongi.core.common.event.ShareStoreUnavailable
import io.soo.bueongi.core.common.event.UnhandledServerError
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 웹 계층이 밖에 알릴 일.
 * 사건을 발행만 한다 — 누가 받아 무엇을 하는지(알림·집계 등)는 여기서 정하지 않는다.
 */
@Component
class ApiEventPublisher(
    private val events: ApplicationEventPublisher,
) {

    /** 위치 공유 저장소(Redis)에 닿지 못했다. */
    fun shareStoreUnavailable(detail: String) {
        events.publishEvent(ShareStoreUnavailable(detail))
    }

    /** 아무도 처리하지 않은 예외가 500 으로 나갔다. */
    fun unhandledServerError(exceptionName: String, detail: String) {
        events.publishEvent(UnhandledServerError(exceptionName = exceptionName, detail = detail))
    }
}
