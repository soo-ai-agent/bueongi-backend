package io.soo.bueongi.core.admin.ops.api.response

import io.soo.bueongi.support.monitoring.AlertRecord

data class AlertsResponse(
    val alerts: List<AlertResponse>,
) {
    companion object {
        fun from(records: List<AlertRecord>): AlertsResponse {
            val alerts: List<AlertResponse> = AlertResponse.from(records)
            return AlertsResponse(alerts = alerts)
        }
    }
}
