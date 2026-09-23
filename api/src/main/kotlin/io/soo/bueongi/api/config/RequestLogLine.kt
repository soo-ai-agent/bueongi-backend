package io.soo.bueongi.api.config

import io.soo.bueongi.core.enums.RequestCaller
import io.soo.bueongi.support.logging.DIRECTION_COLUMN_WIDTH
import io.soo.bueongi.support.logging.DURATION_COLUMN_WIDTH
import io.soo.bueongi.support.logging.INBOUND_DIRECTION
import io.soo.bueongi.support.logging.LOG_COLUMN_SEPARATOR
import io.soo.bueongi.support.logging.STATUS_COLUMN_WIDTH
import io.soo.bueongi.support.logging.TARGET_COLUMN_WIDTH
import io.soo.bueongi.support.logging.padColumn
import io.soo.bueongi.support.logging.traceColumn

data class RequestLogLine(
    val method: String,
    val path: String,
    val status: Int,
    val durationMs: Long,
    val clientId: String?,
    val caller: RequestCaller,
) {
    fun format(): String =
        listOf(
            padColumn(directionAndCaller(), DIRECTION_COLUMN_WIDTH),
            padColumn("$status $method", STATUS_COLUMN_WIDTH),
            padColumn(shortPath(), TARGET_COLUMN_WIDTH),
            "${durationMs}ms".padStart(DURATION_COLUMN_WIDTH),
            traceColumn(),
            "👤 ${clientId ?: UNKNOWN_CLIENT}",
        ).joinToString(LOG_COLUMN_SEPARATOR)

    private fun directionAndCaller(): String =
        when (caller) {
            RequestCaller.ADMIN -> "$INBOUND_DIRECTION 관리자"
            RequestCaller.USER -> "$INBOUND_DIRECTION 사용자"
        }

    private fun shortPath(): String {
        if (path.length <= MAX_PATH_LENGTH) {
            return path
        }
        return path.take(MAX_PATH_LENGTH) + "…"
    }

    fun needsAttention(): Boolean = status >= CLIENT_ERROR_STATUS

    private companion object {
        const val CLIENT_ERROR_STATUS: Int = 400
        const val MAX_PATH_LENGTH: Int = 200
        const val UNKNOWN_CLIENT: String = "unknown"
    }
}
