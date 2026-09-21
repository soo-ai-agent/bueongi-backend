package io.soo.bueongi.support.logging

// cause 는 causeChainOf 로 만든 사슬을 넣는다 — 겉 예외만 넣으면 진짜 원인이 사라진다.
data class ExceptionLogLine(
    val kind: String,
    val counterpart: String,
    val subject: String,
    val cause: String,
    val result: String = NO_RESPONSE_STATUS,
    val action: String = "",
    val paging: String = "",
) {
    fun format(): String {
        val columns: MutableList<String> =
            mutableListOf(
                padColumn("$EXCEPTION_DIRECTION $kind", DIRECTION_COLUMN_WIDTH),
                padColumn(counterpart, COUNTERPART_COLUMN_WIDTH),
                padColumn(result, STATUS_COLUMN_WIDTH),
                padColumn(subject, TARGET_COLUMN_WIDTH),
            )
        val trace: String = traceColumn()
        if (trace.isNotEmpty()) {
            columns.add(trace)
        }
        if (paging.isNotEmpty()) {
            columns.add("📄 $paging")
        }
        columns.add("✖ $cause")
        if (action.isNotEmpty()) {
            columns.add("↳ $action")
        }
        return columns.joinToString(LOG_COLUMN_SEPARATOR).trimEnd()
    }
}

const val UNHANDLED_EXCEPTION: String = "미처리"

const val IGNORED_EXCEPTION: String = "무시"
