package io.soo.bueongi.core.admin.safetyspot.csv

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore
import java.time.Instant

internal class FakeLiveStore : SafetySpotLiveStore {
    private val savedAt: HashMap<Pair<SafetySpotType, String>, Instant> = HashMap()

    var lastType: SafetySpotType? = null
    var lastPoints: List<SafetySpotPoint>? = null

    override fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {
        savedAt[type to regionCode] = Instant.now()
    }

    override fun replaceFiles(type: SafetySpotType, points: List<SafetySpotPoint>) {
        lastType = type
        lastPoints = points
    }

    override fun savedAtOf(type: SafetySpotType, regionCode: String): Instant? {
        return savedAt[type to regionCode]
    }
}
