package io.soo.bueongi.core.admin.safetyspot.csv

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotWriter
import java.time.Instant

internal class FakeSafetySpotWriter : SafetySpotWriter {
    private val savedAt: HashMap<Pair<SafetySpotType, String>, Instant> = HashMap()

    var lastType: SafetySpotType? = null
    var lastPoints: List<SafetySpotPoint>? = null

    override fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {
        savedAt[type to regionCode] = Instant.now()
    }

    override fun replaceUpload(type: SafetySpotType, points: List<SafetySpotPoint>) {
        lastType = type
        lastPoints = points
    }

    override fun findSavedAt(type: SafetySpotType, regionCode: String): Instant? {
        return savedAt[type to regionCode]
    }
}
