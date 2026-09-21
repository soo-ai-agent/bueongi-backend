package io.soo.bueongi.api

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ApiEventPublisher(
    private val events: ApplicationEventPublisher,
) {

    fun shareStoreUnavailable(detail: String) {
        events.publishEvent(OperationEvent(key = STORE_UNAVAILABLE_KEY, title = STORE_UNAVAILABLE_TITLE, detail = detail))
    }

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
