package io.soo.bueongi.core.admin.safetyspot.api.response

import io.soo.bueongi.storage.db.safetyspot.store.CsvRow
import io.soo.bueongi.storage.db.safetyspot.store.CsvRowResult
import io.soo.bueongi.storage.db.safetyspot.store.CsvSkipReason

data class SafetySpotCsvRowResponse(
    val rowNumber: Int,
    val cells: List<String>,
    val status: CsvRowStatus,
    // 빠진 행(SKIPPED)에만 이유가 있다.
    val skipReason: CsvSkipReason?,
) {
    companion object {
        fun from(row: CsvRow): SafetySpotCsvRowResponse =
            when (val result: CsvRowResult = row.result) {
                is CsvRowResult.Loaded -> SafetySpotCsvRowResponse(row.rowNumber, row.cells, CsvRowStatus.LOADED, null)
                is CsvRowResult.Skipped -> SafetySpotCsvRowResponse(row.rowNumber, row.cells, CsvRowStatus.SKIPPED, result.reason)
            }
    }
}
