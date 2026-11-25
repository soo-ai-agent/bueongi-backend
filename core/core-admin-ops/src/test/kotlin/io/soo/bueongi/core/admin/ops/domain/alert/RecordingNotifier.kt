package io.soo.bueongi.core.admin.ops.domain.alert

import io.soo.bueongi.support.monitoring.AlertNotifier

internal class RecordingNotifier : AlertNotifier {
    override val isEnabled: Boolean = true
    val sent: MutableList<Triple<String, String, String>> = ArrayList()

    override fun notify(key: String, title: String, detail: String) {
        sent.add(Triple(key, title, detail))
    }
}
