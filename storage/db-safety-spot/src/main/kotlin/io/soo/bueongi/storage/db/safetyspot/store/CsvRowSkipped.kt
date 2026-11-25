package io.soo.bueongi.storage.db.safetyspot.store

data class CsvRowSkipped(
    val reason: CsvSkipReason,
) : CsvRowResult
