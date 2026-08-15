package io.soo.bueongi.core.route.marker.service

import io.soo.bueongi.core.common.event.OperationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 마커 도메인이 밖에 알릴 일.
 * 사건을 발행만 한다 — 누가 받아 무엇을 하는지(알림·집계 등)는 여기서 정하지 않는다.
 */
@Component
class MarkerEventPublisher(
    private val events: ApplicationEventPublisher,
) {

    /** 안심시설 색인이 통째로 비어 경로변 마커를 만들 수 없다. */
    fun facilityIndexEmpty(detail: String) {
        events.publishEvent(OperationEvent(key = INDEX_EMPTY_KEY, title = INDEX_EMPTY_TITLE, detail = detail))
    }

    companion object {

        private const val INDEX_EMPTY_KEY: String = "facility-index-empty"
        private const val INDEX_EMPTY_TITLE: String = "안심시설 데이터 제공 실패"
    }
}
