package io.soo.bueongi.core.safetyspot.domain.streetlamp

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotWriter
import java.time.Instant

internal class FakeSafetySpotWriter : SafetySpotWriter {
    private val savedAt: HashMap<Pair<SafetySpotType, String>, Instant> = HashMap()

    val appended: ArrayList<SafetySpotPoint> = ArrayList()

    override fun replaceUpload(type: SafetySpotType, points: List<SafetySpotPoint>) {
        appended.clear()
        appended.addAll(points)
    }

    override fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {
        savedAt[type to regionCode] = Instant.now()
        appended.addAll(points)
    }

    override fun findSavedAt(type: SafetySpotType, regionCode: String): Instant? {
        return savedAt[type to regionCode]
    }
}
