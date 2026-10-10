package io.soo.bueongi.core.admin.ops.domain.alert

import io.soo.bueongi.core.common.event.ApiErrorResponded
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

// 운영자에게 알릴 오류 응답 code 는 여기서 고른다 — 새 알림은 분기 하나를 더하면 끝난다.
@Component
class ApiErrorEventListener(
    private val alertNotifier: AlertNotifier,
) {
    @EventListener
    fun onApiErrorResponded(event: ApiErrorResponded) {
        if (event.code == "ROUTE_SEARCH_UNAVAILABLE") {
            alertNotifier.notify(
                key = "tmap-route-failed",
                title = "Tmap 경로 API 실패",
                detail = "경로 후보 전멸 또는 키 미설정 — 앱은 503 안내를 받는다.",
            )
        }
        if (event.code == "PLACE_SEARCH_UNAVAILABLE") {
            alertNotifier.notify(
                key = "place-search-failed",
                title = "장소검색 실패",
                detail = event.causeName ?: "원인 미상",
            )
        }
    }
}
