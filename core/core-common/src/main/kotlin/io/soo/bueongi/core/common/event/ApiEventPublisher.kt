package io.soo.bueongi.core.common.event

import io.soo.bueongi.core.enums.AlertCategory
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
        val key: String = AlertCategory.SHARE_STORE_UNAVAILABLE.key
        events.publishEvent(OperationEvent(key = key, title = "위치 공유 저장소(Redis) 접근 실패", detail = detail))
    }

    fun unhandledServerError(exceptionName: String, detail: String) {
        events.publishEvent(
            OperationEvent(key = AlertCategory.SERVER_ERROR.keyOf(exceptionName), title = "처리되지 않은 서버 오류(500)", detail = detail),
        )
    }
}
