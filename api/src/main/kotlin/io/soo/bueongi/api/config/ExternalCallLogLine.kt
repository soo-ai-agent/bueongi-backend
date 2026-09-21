package io.soo.bueongi.api.config

import io.soo.bueongi.support.logging.BACKGROUND_ORIGIN
import io.soo.bueongi.support.logging.COUNTERPART_COLUMN_WIDTH
import io.soo.bueongi.support.logging.DIRECTION_COLUMN_WIDTH
import io.soo.bueongi.support.logging.DURATION_COLUMN_WIDTH
import io.soo.bueongi.support.logging.LOG_COLUMN_SEPARATOR
import io.soo.bueongi.support.logging.NO_RESPONSE_STATUS
import io.soo.bueongi.support.logging.OUTBOUND_DIRECTION
import io.soo.bueongi.support.logging.STATUS_COLUMN_WIDTH
import io.soo.bueongi.support.logging.currentRequestOrigin
import io.soo.bueongi.support.logging.TARGET_COLUMN_WIDTH
import io.soo.bueongi.support.logging.padColumn
import io.soo.bueongi.support.logging.traceColumn

data class ExternalCallLogLine(
    val provider: String,
    val method: String,
    val endpoint: String,
    val status: Int,
    val durationMs: Long,
    val failureReason: String = "",
    val paging: String = "",
) {
    fun format(): String {
        val columns: MutableList<String> = mutableListOf(
            padColumn(OUTBOUND_DIRECTION, DIRECTION_COLUMN_WIDTH),
            padColumn(provider, COUNTERPART_COLUMN_WIDTH),
            padColumn(statusText(), STATUS_COLUMN_WIDTH),
            padColumn(endpoint, TARGET_COLUMN_WIDTH),
            "${durationMs}ms".padStart(DURATION_COLUMN_WIDTH),
        )
        val trace: String = traceColumn()
        if (trace.isNotEmpty()) {
            columns.add(trace)
        }
        columns.add("↩ $origin")
        if (paging.isNotEmpty()) {
            columns.add("📄 $paging")
        }
        if (failureReason.isNotEmpty()) {
            columns.add("✖ $failureReason")
        }
        return columns.joinToString(LOG_COLUMN_SEPARATOR).trimEnd()
    }

    private val origin: String
        get() = currentRequestOrigin().ifEmpty { BACKGROUND_ORIGIN }

    private fun statusText(): String {
        if (status <= 0) {
            return "$NO_RESPONSE_STATUS $method"
        }
        return "$status $method"
    }

    fun needsAttention(): Boolean = status <= 0 || status >= CLIENT_ERROR_STATUS

    private companion object {
        const val CLIENT_ERROR_STATUS: Int = 400
    }
}
