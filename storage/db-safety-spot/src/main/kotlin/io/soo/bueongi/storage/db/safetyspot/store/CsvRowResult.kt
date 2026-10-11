package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

sealed interface CsvRowResult {
    data class Loaded(
        val point: SafetySpotPoint,
    ) : CsvRowResult

    data class Skipped(
        val reason: CsvSkipReason,
    ) : CsvRowResult
}
