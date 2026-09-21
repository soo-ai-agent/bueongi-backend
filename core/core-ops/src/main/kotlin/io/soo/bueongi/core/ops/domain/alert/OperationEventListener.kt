package io.soo.bueongi.core.ops.domain.alert

import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

// `@Async` 를 걸지 않는다 — AlertNotifier 계약이 이미 호출 스레드를 막지 않는다.
@Component
class OperationEventListener(
    private val alertNotifier: AlertNotifier,
) {

    @EventListener
    fun onOperationEvent(event: OperationEvent) {
        alertNotifier.notify(key = event.key, title = event.title, detail = event.detail)
    }
}
