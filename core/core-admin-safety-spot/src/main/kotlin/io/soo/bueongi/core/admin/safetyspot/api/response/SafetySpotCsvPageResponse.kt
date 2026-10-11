package io.soo.bueongi.core.admin.safetyspot.api.response

import io.soo.bueongi.storage.db.safetyspot.store.CsvPage
import io.soo.bueongi.storage.db.safetyspot.store.CsvRow

data class SafetySpotCsvPageResponse(
    val header: List<String>,
    val rows: List<SafetySpotCsvRowResponse>,
    val totalRows: Int,
    val loadedRows: Int,
    val skippedRows: Int,
    val matchedRows: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun from(page: CsvPage): SafetySpotCsvPageResponse {
            val rows: ArrayList<SafetySpotCsvRowResponse> = ArrayList(page.rows.size)
            for (row: CsvRow in page.rows) {
                rows.add(SafetySpotCsvRowResponse.from(row))
            }
            return SafetySpotCsvPageResponse(
                header = page.header,
                rows = rows,
                totalRows = page.totalRows,
                loadedRows = page.loadedRows,
                skippedRows = page.skippedRows,
                matchedRows = page.matchedRows,
                page = page.page,
                size = page.size,
            )
        }
    }
}
