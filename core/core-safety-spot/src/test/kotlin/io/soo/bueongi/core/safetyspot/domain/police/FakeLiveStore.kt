package io.soo.bueongi.core.safetyspot.domain.police

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore
import java.time.Instant

internal class FakeLiveStore : SafetySpotLiveStore {
    private val savedAt: HashMap<Pair<SafetySpotType, String>, Instant> = HashMap()

    val appended: ArrayList<SafetySpotPoint> = ArrayList()

    override fun replaceFiles(type: SafetySpotType, points: List<SafetySpotPoint>) {
        appended.clear()
        appended.addAll(points)
    }

    override fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {
        savedAt[type to regionCode] = Instant.now()
        appended.addAll(points)
    }

    override fun savedAtOf(type: SafetySpotType, regionCode: String): Instant? {
        return savedAt[type to regionCode]
    }
}
