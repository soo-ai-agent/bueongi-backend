package io.soo.bueongi.storage.db.safetyspot.store

data class CsvRow(
    val rowNumber: Int,
    val cells: List<String>,
    val result: CsvRowResult,
)
