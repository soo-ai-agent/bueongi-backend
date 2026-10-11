package io.soo.bueongi.storage.db.safetyspot.store

data class CsvPage(
    val header: List<String>,
    val rows: List<CsvRow>,
    val totalRows: Int,
    val loadedRows: Int,
    val skippedRows: Int,
    /** 쪽 수를 셀 기준 — '빠진 행만' 보기면 빠진 행 수, 아니면 totalRows 와 같다. */
    val matchedRows: Int,
    val page: Int,
    val size: Int,
)
