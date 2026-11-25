package io.soo.bueongi.core.app

import org.springframework.context.ApplicationEventPublisher

internal class RecordingPublisher : ApplicationEventPublisher {
    val events: MutableList<Any> = ArrayList()

    override fun publishEvent(event: Any) {
        events.add(event)
    }
}
