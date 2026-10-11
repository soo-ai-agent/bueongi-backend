package io.soo.bueongi.core.admin.ops.api.response

import io.soo.bueongi.core.enums.AlertCategory
import io.soo.bueongi.core.enums.AlertSeverity
import io.soo.bueongi.support.monitoring.AlertRecord

data class AlertResponse(
    val atEpochMs: Long,
    val key: String,
    val title: String,
    val detail: String,
    val category: String,
    val label: String,
    val severity: AlertSeverity,
) {
    companion object {
        fun from(record: AlertRecord): AlertResponse {
            val category: AlertCategory? = AlertCategory.ofAlertKey(record.key)
            if (category == null) {
                // 종류를 모르는 key 는 key 의 ':' 앞부분을 그대로 이름으로 보여 준다.
                val unknownKey: String = record.key.substringBefore(":")
                return AlertResponse(
                    atEpochMs = record.atEpochMs,
                    key = record.key,
                    title = record.title,
                    detail = record.detail,
                    category = unknownKey,
                    label = unknownKey,
                    severity = AlertSeverity.UNKNOWN,
                )
            }
            return AlertResponse(
                atEpochMs = record.atEpochMs,
                key = record.key,
                title = record.title,
                detail = record.detail,
                category = category.key,
                label = category.label,
                severity = category.severity,
            )
        }

        fun from(records: List<AlertRecord>): List<AlertResponse> {
            val responses: ArrayList<AlertResponse> = ArrayList(records.size)
            for (record in records) {
                responses.add(from(record))
            }
            return responses
        }
    }
}
