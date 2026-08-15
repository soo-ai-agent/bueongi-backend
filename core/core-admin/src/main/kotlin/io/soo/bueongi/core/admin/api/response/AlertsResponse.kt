package io.soo.bueongi.core.admin.api.response

import io.soo.bueongi.support.monitoring.AlertRecord

data class AlertsResponse(
    val alerts: List<AlertRecord>,
)
