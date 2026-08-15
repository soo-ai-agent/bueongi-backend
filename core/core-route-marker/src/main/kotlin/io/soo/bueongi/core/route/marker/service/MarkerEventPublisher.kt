package io.soo.bueongi.core.route.marker.service

import io.soo.bueongi.core.common.event.FacilityIndexEmpty
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

    /** 안심시설 색인이 비어 마커를 만들 수 없다. */
    fun facilityIndexEmpty(detail: String) {
        events.publishEvent(FacilityIndexEmpty(detail))
    }
}
