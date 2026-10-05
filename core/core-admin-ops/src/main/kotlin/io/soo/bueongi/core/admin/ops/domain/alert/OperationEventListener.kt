package io.soo.bueongi.core.admin.ops.domain.alert

import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class OperationEventListener(
    private val alertNotifier: AlertNotifier,
) {
    @EventListener
    fun onOperationEvent(event: OperationEvent) {
        alertNotifier.notify(key = event.key, title = event.title, detail = event.detail)
    }
}
