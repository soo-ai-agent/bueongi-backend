package io.soo.bueongi.api

import io.soo.bueongi.core.common.event.OperationEvent
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
        events.publishEvent(OperationEvent(key = STORE_UNAVAILABLE_KEY, title = STORE_UNAVAILABLE_TITLE, detail = detail))
    }

    /** 아무도 처리하지 않은 예외가 500 으로 나갔다. 예외 종류마다 키를 갈라 서로 다른 장애를 한 건으로 뭉치지 않는다. */
    fun unhandledServerError(exceptionName: String, detail: String) {
        events.publishEvent(
            OperationEvent(key = "$SERVER_ERROR_KEY_PREFIX$exceptionName", title = SERVER_ERROR_TITLE, detail = detail),
        )
    }

    companion object {

        private const val STORE_UNAVAILABLE_KEY: String = "share-store-unavailable"
        private const val STORE_UNAVAILABLE_TITLE: String = "위치 공유 저장소(Redis) 접근 실패"

        private const val SERVER_ERROR_KEY_PREFIX: String = "api-500:"
        private const val SERVER_ERROR_TITLE: String = "처리되지 않은 서버 오류(500)"
    }
}
