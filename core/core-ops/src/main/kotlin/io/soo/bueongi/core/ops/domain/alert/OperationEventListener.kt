package io.soo.bueongi.core.ops.domain.alert

import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 도메인이 발행한 운영 사건을 받아 알림 수단으로 내보낸다.
 * **도메인은 여기를 모른다** — 알림을 Slack 에서 다른 것으로 바꿔도 도메인은 그대로다.
 *
 * 별도의 `@Async` 를 걸지 않는다. [AlertNotifier] 계약이 이미 "호출 스레드를 막지 않는다"이고,
 * 여기서 또 스레드를 갈아타면 발송 순서만 흐트러진다.
 */
@Component
class OperationEventListener(
    private val alertNotifier: AlertNotifier,
) {

    @EventListener
    fun onOperationEvent(event: OperationEvent) {
        alertNotifier.notify(key = event.key, title = event.title, detail = event.detail)
    }
}
