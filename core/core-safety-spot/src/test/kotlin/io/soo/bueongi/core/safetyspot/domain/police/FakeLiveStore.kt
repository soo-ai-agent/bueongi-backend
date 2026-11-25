package io.soo.bueongi.core.safetyspot.domain.police

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore

internal class FakeLiveStore : SafetySpotLiveStore {
    val appended: ArrayList<SafetySpotPoint> = ArrayList()

    override fun replaceFiles(type: SafetySpotType, points: List<SafetySpotPoint>) {
        appended.clear()
        appended.addAll(points)
    }

    override fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {
        appended.addAll(points)
    }
}
