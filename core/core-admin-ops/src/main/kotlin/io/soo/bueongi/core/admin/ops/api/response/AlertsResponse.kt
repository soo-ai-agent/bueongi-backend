package io.soo.bueongi.core.admin.ops.api.response

import io.soo.bueongi.support.monitoring.AlertRecord

data class AlertsResponse(val alerts: List<AlertRecord>)
