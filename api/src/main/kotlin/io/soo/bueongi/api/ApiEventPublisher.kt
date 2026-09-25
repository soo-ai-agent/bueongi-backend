package io.soo.bueongi.api

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ApiEventPublisher(private val events: ApplicationEventPublisher) {
    fun shareStoreUnavailable(detail: String) {
        events.publishEvent(OperationEvent(key = "share-store-unavailable", title = "위치 공유 저장소(Redis) 접근 실패", detail = detail))
    }

    fun routeSearchUnavailable(detail: String) {
        events.publishEvent(OperationEvent(key = "tmap-route-failed", title = "Tmap 경로 API 실패", detail = detail))
    }

    fun placeSearchUnavailable(detail: String) {
        events.publishEvent(OperationEvent(key = "tmap-place-failed", title = "Tmap 장소검색 실패", detail = detail))
    }

    fun unhandledServerError(exceptionName: String, detail: String) {
        events.publishEvent(
            OperationEvent(key = "api-500:$exceptionName", title = "처리되지 않은 서버 오류(500)", detail = detail),
        )
    }
}
