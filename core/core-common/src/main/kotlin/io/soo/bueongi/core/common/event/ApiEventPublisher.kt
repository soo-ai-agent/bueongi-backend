package io.soo.bueongi.core.common.event

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ApiEventPublisher(
    private val events: ApplicationEventPublisher,
) {
    fun apiErrorResponded(code: String, causeName: String?) {
        events.publishEvent(ApiErrorResponded(code = code, causeName = causeName))
    }

    fun shareStoreUnavailable(detail: String) {
        events.publishEvent(OperationEvent(key = "share-store-unavailable", title = "위치 공유 저장소(Redis) 접근 실패", detail = detail))
    }

    fun unhandledServerError(exceptionName: String, detail: String) {
        events.publishEvent(
            OperationEvent(key = "api-500:$exceptionName", title = "처리되지 않은 서버 오류(500)", detail = detail),
        )
    }
}
