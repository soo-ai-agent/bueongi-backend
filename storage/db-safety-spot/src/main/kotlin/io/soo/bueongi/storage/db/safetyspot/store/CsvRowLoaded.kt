package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

data class CsvRowLoaded(
    val point: SafetySpotPoint,
) : CsvRowResult
