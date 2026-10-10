package io.soo.bueongi.core.admin.ops.domain.alert

import io.soo.bueongi.core.admin.ops.api.response.AlertsResponse
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.AlertRecord
import org.springframework.stereotype.Service

@Service
class AlertHistoryService(
    private val alertNotifier: AlertNotifier,
) {
    fun findRecent(limit: Int): AlertsResponse {
        val alerts: List<AlertRecord> = alertNotifier.findRecentAlerts(limit)
        return AlertsResponse.from(alerts)
    }
}
