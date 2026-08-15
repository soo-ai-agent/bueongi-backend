package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.common.event.SafeRouteFellBack
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 경로 도메인이 밖에 알릴 일. 받는 쪽이 무엇을 하는지 이 도메인은 모른다.
 * 사건을 발행만 한다 — 누가 받아 무엇을 하는지(알림·집계 등)는 여기서 정하지 않는다.
 */
@Component
class RouteEventPublisher(
    private val events: ApplicationEventPublisher,
) {

    /** Tmap 경로 후보가 전멸해 직선거리 추정으로 응답했다. */
    fun safeRouteFellBack(detail: String) {
        events.publishEvent(SafeRouteFellBack(detail))
    }
}
