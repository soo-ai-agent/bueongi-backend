package io.soo.bueongi.core.api.controller.v1.response

import io.soo.bueongi.support.monitoring.AlertRecord

data class AlertsResponse(
    val alerts: List<AlertRecord>,
)
